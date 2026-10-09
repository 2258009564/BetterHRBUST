# BetterHRBUST 介绍页 (`landing/`)

BetterHRBUST 的对外介绍（落地）页面：默认深色、大标题大图标、四端（Web / 油猴 / Windows / Android）介绍，单页静态站点。

技术栈与 `web/` 端完全一致：**Vite 5 + Vue 3 + Tailwind CSS v4**（外加 GSAP 入场动画）。

## 开发

```bash
cd landing
npm install
npm run dev      # 默认 http://localhost:5174/
```

## 构建

```bash
npm run build    # 产物：landing/dist/（相对路径引用，可直接静态托管）
npm run preview  # 本地预览构建产物
```

## 自动部署

`.github/workflows/deploy-landing.yml` 负责介绍页的自动构建与部署：

- **触发**：向 `main` 推送 `landing/**`（或该工作流文件本身）的改动；也可在 Actions 页面手动触发（`workflow_dispatch`）。
- **构建**：全程在 GitHub 云端运行器（`ubuntu-latest`，Node 22）执行 `npm ci && npm run build`，**不在服务器上构建**。
- **部署**：`rsync -avz --delete` 将 `landing/dist/` 内容全量同步到服务器
  `/opt/1panel/www/sites/betterhrbust/index/dist`（`--delete` 会清理历史哈希产物，注意该目录应专用于本页面）。

需要在仓库 **Settings → Secrets and variables → Actions → Secrets** 中配置：

| Secret | 必填 | 说明 |
| --- | --- | --- |
| `SERVER_HOST` | 是 | 服务器地址（域名或 IP） |
| `SERVER_USER` | 是 | SSH 登录用户，需对目标目录有写权限 |
| `SERVER_SSH_KEY` | 是 | SSH 私钥全文（对应公钥需已加入服务器 `~/.ssh/authorized_keys`） |
| `SERVER_PORT` | 否 | SSH 端口，默认 `22` |

要求服务器已安装 `rsync`（1Panel 环境通常已内置），且目标目录的父级 `index/` 已存在。

同步参数使用 `--omit-dir-times --no-owner --no-group`：1Panel 站点目录通常属 root 或其他用户，非属主无法写目录时间戳/属主属性，忽略它们可避免 `rsync: failed to set times ... Operation not permitted`（退出码 23）。若希望完整保留属性，可在服务器上把目录交给部署用户：`chown -R <SERVER_USER> /opt/1panel/www/sites/betterhrbust/index/dist`（注意 1Panel 可能会重置该权限）。

## 说明

- 页面不请求任何后端接口，纯静态托管即可（GitHub Pages / Nginx / 任意对象存储）。
- 所有按钮与卡片均指向真实地址（GitHub Releases / 仓库 / 油猴脚本安装地址）。
- 品牌素材（`public/mascot.png`、`public/BetterHRBUST.png`）复制自仓库
  `docs/assets/mascot.png` 与 `web/public/BetterHRBUST.png`，更新时请同步。
- 遵循 `prefers-reduced-motion`：用户偏好减弱动效时自动关闭入场动画。
