# BetterHRBUST Desktop (Tauri)

Windows 桌面客户端：Tauri（Rust）封装 `web/` 前端，主进程内置本地反向代理，
双击即用，无需浏览器环境。主进程为 Rust 二进制（无 Node 运行时），渲染采用
系统 WebView2，安装体量小（实测 1.0.0）：

| 指标 | 实测 1.0.0 |
| --- | --- |
| 安装包体积 | **2.6MB**（NSIS） |
| 免安装 zip | **3.3MB** |
| 安装后占用 | **9.3MB**（单 exe，前端资产已嵌入） |
| 主进程私有内存 | **15.8MB**（Rust 二进制） |
| 渲染引擎 | 系统 WebView2（Win10/11 自带） |

> WebView2 渲染进程各子进程 WS 合计约 380MB，其中大量为跨进程共享页重复计算。

## 架构

```
WebView2 窗口 → http://127.0.0.1:1950（固定端口，占用向后顺延）
  ├─ 静态文件     web/dist（rust-embed 嵌入二进制，debug 模式读磁盘）
  ├─ /__app/storage → 键值持久化（storage.rs，JSON 文件原子落盘）
  └─ /academic/* → http://jwzx.hrbust.edu.cn（hyper 流式转发）
       ├─ Host/Referer 注入（教务系统校验这两个请求头）
       ├─ Set-Cookie 剥离 Domain 属性（会话 Cookie 落在 127.0.0.1）
       ├─ 302 Location 绝对地址重写为 /academic/ 相对路径
       │   （教务系统仅有 HTTP(80)，绝对地址会被浏览器 HTTPS 自动升级拦截）
       └─ 上游 20s 超时；出错直接断开连接 → 前端 fetch reject，
          复用 client.js 既有的「网络连接失败……请确认是否处于校园网或VPN环境」提示
```

所有请求走相对路径 `/academic/` + Cookie 会话，代理行为与
`web/vite.config.mjs` 的开发代理完全一致。窗口控制经 `shim.js` 注入
`window.desktopWindow`（桥接 Tauri 的 `window.__TAURI__`），权限由
`capabilities/main.json` 的 `remote.urls` 白名单授予（仅窗口控制最小集）。

会话 Cookie 由 WebView2 用户数据目录（按 bundle identifier 固定）持久保存，
重启应用后仍有效，直至教务侧过期。固定端口 1950 保证页面源跨冷启动稳定。

教务数据缓存与会话元数据（成绩 / 考试 / 课表 / 登录时间戳等）经
`/__app/storage` 端点落盘到 `%APPDATA%/edu.hrbust.betterhrbust/storage.json`
（键值 JSON，临时文件 + 原子改名写回，损坏文件保留 `.corrupt` 现场），
由前端存储门面 `web/src/services/storage.js` 在桌面环境自动启用。
数据归 Rust 进程所有：本地端口被占顺延、WebView2 用户数据目录被清理
均不丢数据（端点规则同样有集成测试覆盖）。

反代层刻意不引 axum/tower 全家桶，直接基于 hyper 实现（约 300 行），
以守住上表的体积与内存目标；规则有 Rust 集成测试（mock 教务系统）完整覆盖。

## 环境要求

- Rust ≥ 1.90（rustup stable）
- Node.js ≥ 18
- Windows 10/11 x64（WebView2 Evergreen 随系统分发）
- 访问教务系统需校园网或学校 VPN

## 开发

```powershell
# 1. 构建前端（首次需先 npm install；rust-embed 编译期要求 web/dist 存在）
cd web
npm install
npm run build

# 2. 启动桌面端（tauri dev 自动拉起 Vite 开发服务器并热更新，
#    /academic 代理由 Vite dev server 自带）
cd ../desktop-tauri
npm install
npm run dev
```

## 测试

本地服务层（静态托管、SPA 回退、路径穿越防护、反代规则、断连语义、端口顺延、
键值持久化读写与跨重启恢复）无需启动窗口即可验证，上游用本地 mock 替代，
不依赖校园网：

```powershell
cd desktop-tauri
npm run test    # = cargo test --manifest-path src-tauri/Cargo.toml
```

## 打包

```powershell
cd desktop-tauri
npm run dist    # 完整流程：构建 web + tauri build + 便携 zip
npm run pack    # 仅产出未打包的裸 exe（快速验证）
```

产物位于：

| 文件 | 说明 |
| --- | --- |
| `src-tauri/target/release/bundle/nsis/BetterHRBUST_<版本>_x64-setup.exe` | NSIS 安装包（currentUser 免管理员） |
| `release/BetterHRBUST-<版本>-win.zip` | 免安装绿色版（单 exe，资产已嵌入） |

CI（`.github/workflows/build-desktop.yml`）：推送 main 自动构建并以 Rust 测试为门禁；
打 `desktop-v*` 标签时自动发布 Release。

## 分发说明

- **WebView2**：默认 `downloadBootstrapper`，目标机器缺失运行时时安装器联网引导
  （Win10/11 一般已随系统更新安装，此路径仅兜底）。
- **SmartScreen**：应用未做代码签名，首次运行若被拦截，点击「更多信息 → 仍要运行」。
- **应用图标**：更换时把新的正方形 PNG（建议 1024x1024、透明背景）放到
  `desktop-tauri/build/icon-source.png`，执行 `npm run icon` 重新生成全套，再 `npm run dist`
  （当前图标为项目吉祥物）。
- 更新版本时同步修改 `desktop-tauri/package.json`、`src-tauri/tauri.conf.json`
  与 `src-tauri/Cargo.toml` 的 `version`（与 Android 端 `versionName` 保持一致）。
