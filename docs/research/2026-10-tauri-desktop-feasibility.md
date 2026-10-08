# Tauri 重构桌面客户端可行性调研

> 调研日期：2026-10-08 ｜ 背景：上游已合入 Electron 桌面端（`desktop/`，commit `1d264aa`/`f07fce4`），
> 评估以 Tauri（Rust）重构的可行性；反代层要求尽量复用现有项目。
>
> **实施状态（2026-10-08）**：已落地为 `desktop-tauri/`（与 Electron 版并存过渡），
> Rust 集成测试 12/12 全绿。实施中的偏离：反代层最终未引 axum/axum-reverse-proxy，
> 改为直接基于 hyper 手写转发（约 300 行）——为实现「上游故障断连而非 502」的精确语义，
> 同时砍掉两棵依赖树以守住体积目标（详见 `desktop-tauri/src-tauri/src/proxy.rs` 头注释）。

## 一、结论（TL;DR）

**可行，且架构可以 1:1 平移，前端近乎零改动。** 推荐方案：

- **框架**：Tauri 2.x（当前稳定版 2.12，2026-09 底发布）
- **本地服务**：Rust 侧用 `axum` 起一个只监听 `127.0.0.1:1950`（被占用向后顺延，同 Electron 策略）的 HTTP 服务，静态托管 + `/academic` 反代同源挂载
- **静态托管**：`tower-http` 的 `ServeDir`（SPA 回退、MIME、缓存头、防穿越全部内置）
- **反代**：`axum-reverse-proxy`（活跃维护，2026-08 发布 2.2.0）承担转发主体，三条改写规则
  （Referer 注入、Set-Cookie 域剥离、302 Location 重写）用 tower 中间件实现，预计 ≤ 100 行自定义代码
- **预计收益**：安装包从 ~85MB 降到 ~8-10MB，内存与启动明显改善；代价是构建链引入 Rust 工具链
- **工作量估算**：3~4 人日（熟悉 Rust 的开发者）

不建议用 Cloudflare `pingora`（为 CDN 规模设计，嵌入桌面应用过重），
`tauri-plugin-localhost` 无法承担反代（仅静态资产、无自定义路由），
`hyper-reverse-proxy` 已停更（2022）。

## 二、现状：Electron 桌面端架构（上游刚合入）

```
BrowserWindow → http://127.0.0.1:1950（固定端口，占用则 +1 顺延至多 10 次）
  ├─ 静态文件     web/dist（零依赖手写 server：MIME/SPA 回退/防穿越/缓存头）
  └─ /academic/* → http://jwzx.hrbust.edu.cn（http-proxy 库）
       ├─ Host/Referer 注入（教务系统校验）
       ├─ Set-Cookie Domain 剥离（会话 Cookie 落在 127.0.0.1）
       ├─ 302 Location 绝对地址 → /academic/ 相对路径（教务无 443，防 HTTPS 自动升级）
       └─ 20s 上游超时；出错直接断 socket（让前端走既有网络错误提示）
```

配套能力：preload 白名单 IPC + 前端 `DesktopTitleBar.vue` 自定义标题栏、
单实例锁、外链转系统浏览器、electron-builder 产出 NSIS + 绿色 zip、`smoke.mjs` 冒烟测试。

**该架构的关键约束**（Tauri 方案必须全部满足）：

1. 页面源必须是稳定 `origin`（固定端口）→ localStorage 与教务会话 Cookie 跨冷启动持久
2. 静态与反代**同源**（Cookie 自然带上，前端全相对路径，零改动）
3. 反代只做字节透传（GBK 解码在前端 `client.js` 完成，服务端不碰编码）
4. `http://127.0.0.1` 在 Chromium 系 webview 中是 secure context，无混合内容限制

## 三、Tauri 现状（2026-10）

- 稳定版 **2.12**（2026-09 底），Windows 依赖 **WebView2**（Evergreen 随 Windows Update 分发，
  Win10/11 普遍预装）；MSRV 1.90；已放弃 Win7
- 官方插件生态齐全：`single-instance`（单实例+聚焦）、`updater`（自动更新，当前 Electron 版反而没有）、
  `opener`（外链转系统浏览器）、`window-state`（记住窗口大小，可选增强）
- Windows 打包：NSIS `setup.exe`（支持 `currentUser` 免管理员安装）与 WiX `.msi` 均为内置；
  WebView2 缺失时安装器可自动走 bootstrapper 下载（默认模式 0MB 体积增量）
- 典型体积：安装包 ~5-10MB（Electron 对比 ~85MB+），运行内存也显著更低（复用系统 WebView2）

## 四、架构映射（Electron → Tauri）

| Electron 组件 | Tauri 对应 | 差异/说明 |
| --- | --- | --- |
| `main.mjs` 主进程 | `src-tauri/src/lib.rs` setup | 窗口创建、导航守卫全部有对应 API |
| `local-server.mjs` 手写静态服务 | `tower-http::ServeDir` + fallback | 现成 crate 覆盖全部特性，代码量≈0 |
| `academic-proxy.mjs`（http-proxy） | `axum-reverse-proxy` + 3 个 tower 中间件 | 见下节选型 |
| 固定端口 1950 + 顺延 | Rust `TcpListener` 绑定循环，同策略 | 几行代码 |
| preload IPC（win:minimize 等） | capability `remote.urls` + `@tauri-apps/api` | **关键差异，见 §6.1** |
| `-webkit-app-region: drag` | `data-tauri-drag-region` 属性 / `app-region` CSS | 见 §6.2 |
| `app.requestSingleInstanceLock` | `tauri-plugin-single-instance` | 官方插件，回调里聚焦窗口 |
| `shell.openExternal` + 导航守卫 | `tauri-plugin-opener` + `on_navigation` | 1:1 |
| 持久会话（userData 目录） | WebView2 用户数据目录（按 bundle id） | 同样跨冷启动持久 |
| electron-builder NSIS+zip | Tauri NSIS；绿色 zip 需自行压缩 release 产物 | zip 非一等公民，见 §6.5 |
| `ELECTRON_START_URL` 直连 Vite | `devUrl: http://localhost:5173`（原生支持） | 更顺，`cargo tauri dev` 热更新 |

## 五、Rust 反代层选型（尽量用现有项目）

需求：`/academic/*` → `http://jwzx.hrbust.edu.cn`，纯 HTTP 无 TLS，低 QPS 单用户，
但有三条定制改写规则与超时断连语义。

| 候选 | 状态 | 结论 |
| --- | --- | --- |
| **`axum-reverse-proxy`** 2.2.0 | 活跃（2026-08 发布，~2 万月下载，MIT，MSRV 1.88） | ✅ **推荐** |
| `pingora`（Cloudflare） | 活跃，生产级代理框架 | ❌ 为 CDN/LB 规模设计，依赖与抽象过重 |
| `rpxy` | 活跃，但定位是独立反代服务器（TLS 多证书等） | ❌ 非嵌入式库用法 |
| `hyper-reverse-proxy` | 2022-03 后停更 | ❌ |
| `tauri-plugin-localhost` | 官方插件 | ❌ `tiny_http` 仅吐打包资产，无自定义路由/代理 |
| 手写 reqwest 转发 | — | 备选（~100 行），但不满足"用现有项目" |

**推荐组合的代码形态**（示意）：

```rust
// Cargo.toml: axum, tower-http, axum-reverse-proxy(default-features=false, 纯 HTTP), tokio

let proxy = ReverseProxy::new("/academic", "http://jwzx.hrbust.edu.cn");

let app = Router::new()
    // 静态：SPA 回退到 index.html，/assets 长缓存由 ServeDir + CacheLayer 处理
    .fallback_service(ServeDir::new(web_root).fallback(ServeFile::new(index_html)))
    .nest_service("/academic", proxy)
    .layer(TimeoutLayer::new(Duration::from_secs(20)))
    .layer(map_request_layer(inject_referer))   // Referer: http://jwzx.hrbust.edu.cn/academic/
    .layer(map_response_layer(rewrite_academic)); // Set-Cookie 域剥离 + 302 Location 重写
```

要点：

- **Host 注入**：`axum-reverse-proxy` 提供 `HostBehaviour` 枚举控制上游 Host 头（等价 node
  `changeOrigin: true`），无需手写
- **Referer 注入**：tower `map_request` 中间件，约 10 行
- **Set-Cookie Domain 剥离 / 302 Location 重写**：tower `map_response` 中间件，
  正则/字符串替换逻辑从 `vite.config.mjs` 平移，约 30-40 行
- **超时**：`TimeoutLayer(20s)`；上游错误语义（断连而非 502 页）需确认 crate 错误路径
  映射为 `ConnectionClosed`/abort —— 若不满足，这是唯一可能需要绕过 crate 的点（回退手写 handler 也在 100 行内）
- 静态侧 `ServeDir` 内置 MIME、路径穿越防护、`If-None-Match` 协商缓存，比 Electron 版手写 server 更省

## 六、关键技术点逐项验证

### 6.1 远程 origin 页面的 IPC（最大架构差异）

Electron 里页面源是 `http://127.0.0.1:1950`（远程于 preload 注入 `window.desktopWindow`）。
Tauri v2 默认只对自有 `tauri://` 内容开放 IPC，**但 capability 显式支持远程 URL**：

```json
{
  "identifier": "titlebar",
  "windows": ["main"],
  "remote": { "urls": ["http://127.0.0.1:1950", "http://127.0.0.1:1951", "...至 1959",
                        "http://localhost:5173"] },
  "permissions": [
    "core:window:allow-minimize",
    "core:window:allow-toggle-maximize",
    "core:window:allow-close",
    "core:window:allow-is-maximized",
    "core:window:allow-start-dragging"
  ]
}
```

端口顺延的 10 个端口全部列入即可（远程 URL 必须构建期静态已知，不支持任意通配）。
权限只给窗口控制最小集。前端侧 `DesktopTitleBar.vue` 增加对
`window.__TAURI__`（`withGlobalTauri: true`）的特性检测适配层，`main.js` 的
`desktop-chrome` 标记同理——改动集中在两个文件，浏览器/油猴端零影响。

### 6.2 自定义标题栏

- `decorations: false` 平替 Electron `titleBarStyle: 'hidden'`
- 拖拽：`data-tauri-drag-region` 属性（注意只对直接挂载的元素生效，子元素需逐个挂）；
  WebView2 新版也认 `app-region: drag` CSS（对触控/笔拖拽更友好，官方文档建议两者并用）
- **双击最大化需手动实现**（Electron 的 `-webkit-app-region` 免费自带，Tauri 要在
  mousedown 里判断 `e.detail === 2` 自行调用 `toggleMaximize()`），约 10 行
- 最大化状态同步：`onResized` + `isMaximized()`，平替现在的 `win:maximize-changed` 事件

### 6.3 会话与 localStorage 持久化

WebView2 的用户数据目录由 Tauri 按 bundle identifier 固定（`%LOCALAPPDATA%/<identifier>`），
Cookie 与 localStorage 跨冷启动持久。固定端口 1950 保证 origin 稳定 → 与 Electron 行为一致，
升级换壳不丢登录态（前提：保持同一端口策略）。

### 6.4 GBK 编码 / Secure Context

- `http://127.0.0.1` 在 WebView2 同属 secure context（与 Electron/Chromium 一致）
- GBK 解码在前端 `TextDecoder('gbk')` 完成，WebView2 是完整版 Chromium（非精简 ICU），
  支持该编码标签；反代继续字节透传，无新风险

### 6.5 打包与分发

- NSIS `setup.exe`：内置支持，`currentUser` 模式免管理员，SmartScreen 现状与 Electron 版相同（无签名）
- **绿色 zip 非一等公民**：Tauri bundler 只出 NSIS/MSI；绿色版需对
  `cargo build --release` 的裸 exe + 资源自行 zip（CI 里一个步骤，可复刻现有 zip 产物形态）
- WebView2 缺失场景：默认 `downloadBootstrapper` 0MB 增量、联网引导安装；
  需要完全离线可切 `offlineInstaller`（体积 +127MB，不建议）
- 图标：现有 `icon-source.png` → `tauri icon` 命令一键生成全套

### 6.6 CI

- `tauri-apps/tauri-action` 官方 GitHub Action，支持打 tag 自动出 Release（与现有
  userscript 发布流并列新增一个 job）；构建机需 Rust 工具链（`dtolnay/rust-toolchain` + 缓存）
- 上游仓库无 macOS/Linux 需求（Electron 版也仅 win x64），无需交叉编译

## 七、风险与遗留差异

| 风险 | 等级 | 缓解 |
| --- | --- | --- |
| WebView2 运行时缺失（精简版 Win10 LTSC 等） | 低 | NSIS 默认 bootstrapper 自动引导；真离线环境少见 |
| `axum-reverse-proxy` 错误语义与"断 socket 不回 502"不完全一致 | 中 | PoC 首项验证；不满足则该路由退手写 handler（~100 行） |
| 双击最大化/拖拽细节与 Electron 手感差异 | 低 | §6.2 手动实现即可对齐 |
| 贡献者门槛：桌面端开发/构建需 Rust 工具链 | 中 | 文档写清；web/油猴端不受影响 |
| Tauri 大版本升级（3.x）迁移成本 | 低 | 2.x 刚发 2.12，生态稳定，锁 minor |
| 双桌面端并存期维护成本 | 中 | 迁移完成后删除 `desktop/`，或仅保留 Tauri 分支 |

## 八、迁移路线建议（若立项）

1. **PoC（0.5 天）**：`cargo tauri init` + axum 服务 + `axum-reverse-proxy` 挂 `/academic`，
   首要验证 §7 第 2 条错误语义；本地连真实教务系统过一遍登录→课表流程
2. **窗口与集成（0.5-1 天）**：frameless + 标题栏适配层、single-instance、导航守卫、capability
3. **前端适配（0.5 天）**：`DesktopTitleBar.vue` / `main.js` 的 Tauri 检测分支（保留 Electron 分支直至切换完成）
4. **测试与打包（1 天）**：把 `smoke.mjs` 场景移植为 Rust 集成测试
   （axum `tower::ServiceExt::oneshot`，无需起进程）；NSIS + zip 产物；`tauri-action` CI job
5. **文档与切换（0.5 天）**：`desktop-tauri/README.md`、根 README 更新、下线 Electron 版

## 九、参考

- Tauri 2.12 发布说明与文档：<https://v2.tauri.app>、<https://crates.io/crates/tauri>
- 远程 URL capability：<https://v2.tauri.app/reference/config>（`capabilities.remote.urls`）
- 窗口自定义（拖拽区/权限）：<https://v2.tauri.app/learn/window-customization/>
- Windows 安装器与 WebView2 策略：<https://v2.tauri.app/distribute/windows-installer/>
- `axum-reverse-proxy`：<https://lib.rs/crates/axum-reverse-proxy>（2.2.0，2026-08）
- `hyper-reverse-proxy` 停更佐证：<https://lib.rs/crates/hyper-reverse-proxy>
- 官方插件仓库（single-instance/updater/opener/localhost 等）：<https://github.com/tauri-apps/plugins-workspace>
