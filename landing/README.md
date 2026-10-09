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

## 说明

- 页面不请求任何后端接口，纯静态托管即可（GitHub Pages / Nginx / 任意对象存储）。
- 所有按钮与卡片均指向真实地址（GitHub Releases / 仓库 / 油猴脚本安装地址）。
- 品牌素材（`public/mascot.png`、`public/BetterHRBUST.png`）复制自仓库
  `docs/assets/mascot.png` 与 `web/public/BetterHRBUST.png`，更新时请同步。
- 遵循 `prefers-reduced-motion`：用户偏好减弱动效时自动关闭入场动画。
