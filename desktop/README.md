# BetterHRBUST Desktop

Windows 桌面客户端：Electron 封装 `web/` 前端，主进程内置本地反向代理，
双击即用，无需浏览器与 Node 环境。

## 架构

```
BrowserWindow → http://127.0.0.1:<随机端口>
  ├─ 静态文件     web/dist（打包后位于 resources/webapp）
  └─ /academic/* → http://jwzx.hrbust.edu.cn
       Host / Referer 注入、Cookie 域剥离、302 Location 重写
```

前端代码零改动：所有请求走相对路径 `/academic/` + Cookie 会话，本地代理
行为与 `web/vite.config.mjs` 的开发代理完全一致（同一底层库 `http-proxy`，
配置 1:1 平移）。服务仅监听 `127.0.0.1`，不对局域网暴露。

会话 Cookie 由 Electron 默认持久会话保存于用户目录，重启应用后仍有效，
直至教务侧过期。

## 环境要求

- Node.js ≥ 18
- Windows 10/11 x64
- 访问教务系统需校园网或学校 VPN

## 开发

```powershell
# 1. 构建前端（首次需先 npm install）
cd web
npm install
npm run build

# 2. 安装桌面端依赖并启动（加载 web/dist）
cd ../desktop
npm install
npm start

# 前端热更新调试：另开终端运行 web 的 vite dev（npm run dev），
# 然后让桌面端直连开发服务器（其自身带 /academic 代理）
$env:ELECTRON_START_URL = 'http://localhost:5173'
npm start
```

## 打包

```powershell
cd desktop
npm run dist        # 完整流程：构建 web + electron-builder
npm run pack        # 仅输出未打包目录（快速验证）
```

产物位于 `desktop/release/`：

| 文件 | 说明 |
| --- | --- |
| `BetterHRBUST-Setup-<版本>.exe` | NSIS 安装包 |
| `BetterHRBUST-<版本>-win.zip` | 免安装绿色版 |

> 国内网络加速（可选）：安装与打包过程需下载 Electron 及构建工具，可预先
> 设置镜像环境变量：
>
> ```powershell
> $env:ELECTRON_MIRROR = 'https://npmmirror.com/mirrors/electron/'
> $env:ELECTRON_BUILDER_BINARIES_MIRROR = 'https://npmmirror.com/mirrors/electron-builder-binaries/'
> ```

## 分发说明

- **SmartScreen**：应用未做代码签名（自签名证书对 SmartScreen 无实质帮助），
  首次运行若被拦截，点击「更多信息 → 仍要运行」即可。
- **应用图标**：把新的正方形 PNG（≥ 256x256，建议 1024x1024、透明背景）放到
  `desktop/build/icon-source.png`，然后执行
  `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\make-icon.ps1`
  生成 `build/icon.ico`（未提供 `icon-source.png` 时回退使用 `web/public/HRBUST.png`）。
  更换图标后需重新 `npm run dist`——图标在打包时嵌入 exe，不会热更新。
- 更新版本时同步修改 `desktop/package.json` 的 `version` 字段。

## 冒烟测试

无需启动 Electron 即可验证本地服务行为（静态托管、SPA 回退、路径穿越防护、
反代错误路径与 302 重写）：

```powershell
node scripts/smoke.mjs
```
