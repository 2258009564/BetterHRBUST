<p align="center">
  <img src="docs/assets/mascot.png" width="200" alt="BetterHRBUST 吉祥物" />
</p>

<h1 align="center">BetterHRBUST ✨</h1>

<p align="center">现代化哈理工教务在线 · 课程表 · 成绩 GPA · 考试日程 · Web / 油猴 / Windows 桌面端 / Android</p>

<p align="center">
  <a href="./LICENSE"><img src="https://img.shields.io/badge/License-MIT-blue.svg" alt="License" /></a>
  <a href="./web/"><img src="https://img.shields.io/badge/Web-Vue%203-42b883.svg" alt="Web" /></a>
  <a href="./android/"><img src="https://img.shields.io/badge/Android-Kotlin%20%2B%20Compose-3DDC84.svg" alt="Android" /></a>
  <a href="./desktop-tauri/"><img src="https://img.shields.io/badge/Desktop-Tauri%202-FFC131.svg" alt="Desktop" /></a>
  <a href="./desktop-tauri/"><img src="https://img.shields.io/badge/Platform-Windows-0078D6.svg" alt="Platform" /></a>
  <a href="https://github.com/Glassous/BetterHRBUST/actions/workflows/build-desktop.yml"><img src="https://github.com/Glassous/BetterHRBUST/actions/workflows/build-desktop.yml/badge.svg" alt="Desktop CI" /></a>
  <a href="https://github.com/Glassous/BetterHRBUST/actions/workflows/build-userscript.yml"><img src="https://github.com/Glassous/BetterHRBUST/actions/workflows/build-userscript.yml/badge.svg" alt="Userscript CI" /></a>
</p>

<!-- 快捷下载：发版时请把下方三条 releases/latest/download 链接中的版本号（1.0.0）同步替换为新版本 -->
<p align="center">
  <a href="https://github.com/Glassous/BetterHRBUST/releases/latest"><img src="https://img.shields.io/badge/下载-Windows_安装版-0078D6?logo=windows&logoColor=white" alt="下载 Windows 安装版" /></a>
  <a href="https://github.com/Glassous/BetterHRBUST/releases/latest"><img src="https://img.shields.io/badge/下载-Windows_便携版-5C2D91?logo=windows&logoColor=white" alt="下载 Windows 便携版" /></a>
  <a href="https://github.com/Glassous/BetterHRBUST/releases/latest"><img src="https://img.shields.io/badge/下载-Android_APK-3DDC84?logo=android&logoColor=white" alt="下载 Android APK" /></a>
  <a href="https://raw.githubusercontent.com/Glassous/BetterHRBUST/dist/better-hrbust.user.js"><img src="https://img.shields.io/badge/安装-油猴脚本-990000?logo=tampermonkey&logoColor=white" alt="安装油猴脚本" /></a>
  <a href="https://github.com/Glassous/BetterHRBUST/releases"><img src="https://img.shields.io/badge/全部版本-Releases-181717?logo=github&logoColor=white" alt="全部版本" /></a>
</p>

自制的现代化哈尔滨理工大学教务在线（URP）客户端：Web / 油猴 / Windows 桌面端 / Android 原生四端同源。

Android 源码版本为 **1.1.1（versionCode 3）**，新增教学评价助手与资料查找，同步 HTTP 登录与重定向修复；修复验证码刷新并发、旧图片残留、网络错误提示和 Cookie 路径隔离。资料索引来自教务处公开下载栏目，包含 **9 个分类、97 条资料及 126 个附件链接**；网页与 Android 使用同一份索引。正式安装包以 [Releases](https://github.com/Glassous/BetterHRBUST/releases) 中已经发布的文件为准。

新版发布页位于 [`landing/`](landing/README.md)，提供浅色/深色主题、平台下载入口和使用指南。GitHub Pages 工作流只在原仓库 `Glassous/BetterHRBUST` 的 `main` 分支部署，PR 和 fork 只构建验证。

哈理工教务在线（`http://jwzx.hrbust.edu.cn/academic/`）基于清华教育在线 / 优慕课 URP 架构，为早期 JSP 应用：缺乏公开 API 与官方文档、网页编码不统一（GBK / UTF-8 混用）、真实功能路径深藏于模块调度器后。

**BetterHRBUST** 致力于将其重塑为现代、优雅、跨平台且具备离线体验的教务工作台：100% 真实教务直连，零模拟假数据，并提供完整的逆向接口文档与自动化探测工具。

```
BetterHRBUST/
├── docs/api/        # 接口逆向文档（13 篇，手写整理，详细踩坑记录）
├── tools/probe/     # 接口探测与逆向工具（零依赖 Node.js CLI + 网页控制台）
├── web/             # 现代化 Web 客户端（Vue 3 + Vite + Tailwind CSS）
├── android/         # Android 原生客户端（Kotlin + Jetpack Compose + Room）
└── desktop-tauri/   # Windows 桌面端（Tauri + Rust，内置本地反向代理）
```

---

## 🌟 核心特性

- **教学评价助手**：从学校读取评价课程和真实问卷，使用你选择的选项与评语逐门提交。每门课程重新取得隐藏字段，提交后读取学校列表核对；问卷结构不同、记录重复或状态未确认时停止，不自动重发 POST。学校未开放评价或返回空列表时，不能进行真实提交。
- **资料查找**：按标题与分类检索教学管理、教务管理、学籍管理、实践教学等公开资料，打开学校原文和附件。不复制附件文件、不处理登录 Cookie；目录索引可离线搜索，访问原文及下载仍需网络。抓取时间显示在页面内。

- **100% 真实教务直连**：不使用任何模拟数据，数据全部直连哈理工教务在线系统，保障准确性。
- **独立现代化登录**：简洁高效的单列登录面板，支持验证码实时刷新、学号记住与会话快速切换；切换前结束旧会话并刷新验证码，核对返回的学号后才更新身份与缓存，旧同步结果不会覆盖新账号；按服务端实际错误区分学号/密码错误、账号不存在与验证码错误，表单标签不会覆盖错误原因。
- **登录即全量同步 · 之后只读本地**：登录成功后一次性拉取全部教务数据并持久化；日常打开与切换页面只读本地缓存，**不再自动请求教务**。仅在「每天首次打开」与「手动刷新」时联网，刷新入口位于顶栏（Android 为「更多」页首行）。
- **离线持久化与柔性会话提示**：教务 Session 失效或断网时自动无缝加载本地缓存；会话失效提示按周节流——刚登录一周内不打扰，仅在登录页告知已失效，手动刷新则要求重新登录。
- **概览工作台**：
  - 今日剩余课程展示（课程下课后不再计入，全部结束后显示 0 门）
  - 近期考试速览（智能时间过滤：未来考试与过去 7 天内考试，历史状态统一为“已结束”）
  - 学业核心指标（已获得学分 / 培养方案总学分、平均学分绩点 GPA、挂科门数），其中已获得学分与培养方案页**同口径**（同一门课只计一次、已通过的课程按方案归集），并附学位绩点 / 补考重修 / 挂科学分三项特色算法摘要，以及可展开的 GPA 公式与统计口径说明
- **智能课程表**：支持按周次切换、当前周高亮、单双周过滤、节次时段分布与课程详情。
- **成绩与 GPA 分析**：历年成绩汇总、按学期/课程性质筛选、不及格标记；采用**哈理工官方五分制绩点**（60 分 = 1 绩点，90—100 分 = 4.0—5.0；五级制 优秀 4.5 / 良好 3.5 / 中等 2.5 / 及格 1.5），重修与补考记录自动合并去重。
- **学业统计仅统计必修课**：GPA、加权平均分、优秀率、不及格门数、累计挂科学分与学业风险预警**全部只统计必修课**，限选与任选不参与上述基础指标；学位绩点采用独立的两门通识选修规则，培养方案按已通过课程归集，避免选修课拉低或虚高学业指标。
- **特色学业算法**：除标准 GPA 外，另提供**学位证算法**（学业课加最高 E 类一门、再加剩余 A–E 类最高一门，排除 0 学分；平均学分绩点 ≥ 1.5）、**推免资格自检**（必修课全部合格、补考重修累计 ≤ 2 门）、**学业风险预警**（必修课累计挂科学分判定留降级 / 退学），阈值以学校教务处口径为准。
- **培养方案与学分进度**：与教务培养方案深度对齐，直观展示各课组学分要求与已获进度；读取方案真实总要求，不虚构 160 学分。专业选修课显示为 X 门候选中选 4 门，学分要求读取学校课组数值；当前软件工程课组要求为 10 学分，不将候选课程的全部学分累加。
- **考试日程与倒计时**：清晰掌握考场、座号、时间及考试倒计时。
- **空教室检索**：按校区、教学楼、周次推算空闲自习教室。
- **学籍档案与我的课程名录**：学籍关键信息一览，查看自己本学期修读的课程。
- **原生 Android 客户端**：Kotlin + Jetpack Compose 原生实现，Material You 动态取色与深浅色主题、边到边全屏 + 悬浮胶囊导航坞、自适应双栏成绩页（列表 / 明细）、下拉刷新；Room 本地持久化 + DataStore 会话偏好，离线同样可用。
- **版本更新检测**：桌面端（设置页底部）与 Android 端（「更多」页底部）内置基于 GitHub Release 的更新检测——启动时静默检查一次（按天节流），并提供手动「检查更新」；发现新版本时展示版本号与更新日志，一键跳转 Release 页面自行下载。网页与油猴版不显示该模块。
- **四端统一口径**：Web、油猴、桌面端与 Android 共用同一套业务口径（API 路径、解析规则、五分制绩点与特色算法、缓存与刷新策略），任一端的结果可互相印证。

---

## 🚀 快速上手

### 1. 网页客户端 (`web/`)

运行现代 Web 界面：

```bash
# 进入前端目录
cd web

# 安装依赖
npm install

# 启动开发服务器（已配置教务在线反向代理）
npm run dev
```

启动后访问提示的本地地址（如 `http://localhost:5173/`）。

> [!TIP]
> 哈理工教务在线运行于校园网环境。在校外访问时，请确保已连接校园 VPN 或校园 WebVPN。

构建生产版本：

```bash
npm run build
```

---

### 2. Android 客户端 (`android/`)

在「更多」页进入「教学评价助手」或「资料查找」。Android 网络层保留 HTTP，限制同源跳转，禁止自动重放表单 POST；教学评价采用 GBK 编码并刷新每门课程的隐藏令牌。

`.github/workflows/android.yml` 在 PR 与代码推送时运行单元测试、Lint、Debug 与 Release 构建，保存 APK 和报告。Debug APK 只用于测试，Release APK 仍需维护者使用正式签名密钥签名后发布，不能用调试签名替代正式升级签名。

Kotlin + Jetpack Compose 编写的原生 Android 客户端，与 Web 端共用同一套业务口径（接口路径、HTML 解析规则、五分制绩点与特色算法、缓存与刷新策略）。

**环境要求**：Android Studio（或 JDK 21）+ Android SDK；运行设备 **Android 11（API 30）及以上**。

```bash
cd android

# 构建 Debug 安装包（产物：app/build/outputs/apk/debug/app-debug.apk）
./gradlew :app:assembleDebug

# 运行单元测试（JVM）
./gradlew :app:testDebugUnitTest
```

Windows 下把 `./gradlew` 换成 `gradlew.bat` 即可：

```powershell
cd android
.\gradlew.bat :app:assembleDebug
```

构建完成后用 `adb install -r app\build\outputs\apk\debug\app-debug.apk` 安装到设备，或直接在 Android Studio 中运行。
开启 R8 压缩后 Release 包体量约 **3.8 MB**（未签名 `app-release-unsigned.apk`），Debug 包约 66 MB（含调试符号与未压缩资源）。

> [!NOTE]
> Release 构建（`./gradlew :app:assembleRelease`）已启用 R8 代码压缩与资源压缩（规则见 `app/src/main/keepRules/`），
> 但**未内置签名配置**，正式分发前需自行在 `app/build.gradle.kts` 中补充 `signingConfigs`。
> 因教务在线为 `http` 明文站点，Manifest 中显式开启了 `usesCleartextTraffic`。

> [!IMPORTANT]
> **安装包命名格式**：发布到 GitHub Release 时统一命名为 `BetterHRBUST-<版本>-android.apk`
> （如 `BetterHRBUST-1.0.0-android.apk`，构建产物 `app-release-unsigned.apk` 重命名即可），
> 与桌面端安装版 `BetterHRBUST-<版本>-windows-setup.exe`、便携版 `BetterHRBUST-<版本>-windows-portable.zip`
> 保持同一命名格式，便于用户识别与 README 快捷下载链接对齐。
> 详见「[发布 Release 与更新检测](#5-发布-release-与更新检测)」。

**客户端特性**：

| 模块 | 说明 |
| --- | --- |
| 登录 | 学号 / 密码 / 验证码，支持验证码点击刷新；会话 Cookie 由自定义 `CookieJar` 托管 |
| 概览 | 学生信息卡（姓名、学号、学院专业、当前教学周）、五分制 GPA / 已获学分 / 未通过门数、特色算法摘要、近期考试倒计时、今日课程、功能九宫格 |
| 课程表 | 周次滑杆切换、按天查看、单双周过滤、课程详情底部弹窗 |
| 成绩 | **自适应双栏**（平板 / 横屏为列表 + 明细并排）、课程搜索、选课属性与及格状态筛选、补考重修合并标记、成绩明细 |
| 考试 | 近期考试倒计时轮播 + 全部考试清单 |
| 培养方案 | 毕业进度总览、各课组「已获 / 要求」学分对照、课组内课程展开 |
| 空教室 / 我的课程名录 / 学籍档案 | 教室占用推算、本人本学期课程查询、学籍基本信息与异动记录 |
| 更多与设置 | 首行**手动刷新数据**按钮、教务公告、深浅色主题（系统 / 浅色 / 深色）、Material You 动态取色、清理离线缓存、退出登录 |

> [!TIP]
> 与 Web 端一致，Android 端也在**登录时一次性全量拉取并持久化**，之后仅「每天首次打开」与「手动刷新」联网；
> 会话失效提示同样按周节流，不会反复打扰。

---

### 3. 油猴脚本客户端（Tampermonkey）

无需本地启动 dev server：可一键将整个客户端打包为**单文件油猴脚本**，安装后直接访问教务在线（`http://jwzx.hrbust.edu.cn/`）即自动接管旧版 JSP 页面。所有请求与原版教务系统完全同源（Cookie 会话、验证码、GBK 解码行为一致），无任何中转服务。

```bash
cd web
npm ci
npm run build:userscript   # 产物: web/dist-userscript/better-hrbust.user.js（单文件，样式与图片全内联）
```

安装与使用说明见 [`web/USERSCRIPT.md`](./web/USERSCRIPT.md)。合并后 GitHub Actions 会自动构建并发布到 `dist` 分支，普通用户可直接从下面的 raw 地址一键安装（油猴凭 `@updateURL` 自动检查更新）：

```
https://raw.githubusercontent.com/Glassous/BetterHRBUST/dist/better-hrbust.user.js
```

> [!TIP]
> 油猴版与 Web 版共用同一套业务代码（client / api / parser / views），**零改动**。
> 选课等写操作请通过油猴菜单「查看原版教务系统」回到官方页面办理。

---

### 4. Windows 桌面端 (`desktop-tauri/`)

Tauri（Rust）封装的桌面客户端：主进程内置仅监听 `127.0.0.1` 的本地反向代理
（与开发代理同一套规则），静态托管 `web/dist`，前端代码零改动，
教务会话 Cookie 持久化于 WebView2 用户数据目录；教务数据缓存与会话元数据
经 Rust 后端 `/__app/storage` 端点落盘（`storage.json` 原子写），端口漂移或
WebView 数据被清理均不丢。体量轻量：安装包 ≤ 10MB、
单 exe 绿色版（前端资产嵌入二进制）、主进程为 Rust 实现（无 Node 运行时）、
渲染采用系统 WebView2，并有 mock 教务系统的 Rust 集成测试完整覆盖。

```powershell
cd desktop-tauri
npm install
npm run dist    # 构建 web + 打包 + 统一命名，产物见 desktop-tauri/release/
```

架构、体积/内存目标、开发与分发细节见
[`desktop-tauri/README.md`](./desktop-tauri/README.md)。

---

### 5. 发布 Release 与更新检测

桌面端与 Android 端的更新检测均读取 GitHub 的 **latest Release**
（`https://api.github.com/repos/Glassous/BetterHRBUST/releases/latest`，请求按天节流）：

| 端 | 更新检测入口 | 当前版本来源 |
| --- | --- | --- |
| Windows 桌面端 | 设置页最底部「版本更新」卡片（**仅 Tauri 端渲染**，网页 / 油猴不显示） | `tauri.conf.json` 的 `version`（运行时经 `getVersion()` 读取） |
| Android | 「更多」页最底部「关于与更新」 | `app/build.gradle.kts` 的 `versionName` |
| Web / 油猴 | 不显示更新模块（油猴凭 `@updateURL` 自动更新） | — |

**Release 产物命名**（统一格式 `BetterHRBUST-<version>-<平台>[-<形态>].<扩展名>`）：

| 平台 | 文件名 | 生成方式 |
| --- | --- | --- |
| Windows 安装版 | `BetterHRBUST-<version>-windows-setup.exe` | CI / `npm run dist` 自动统一命名 |
| Windows 便携版 | `BetterHRBUST-<version>-windows-portable.zip` | 同上（单文件 exe 压缩，解压即用） |
| Android | `BetterHRBUST-<version>-android.apk` | `./gradlew :app:assembleRelease` 后重命名 |

**发布流程**：

1. 桌面端产物从 Actions 的 `betterhrbust-desktop-windows` 下载（已统一命名）；Android 本地打包后重命名；
2. 打 tag `v<version>`（如 `v1.0.0`）并创建 Release，标题建议 `BetterHRBUST v<version>`，正文填写更新日志；
3. 上传上述三个产物；
4. 把 README 顶部「快捷下载」徽章链接中的版本号同步替换为新版本。

> [!TIP]
> **tag 统一用 `v<version>`**：桌面端与 Android 共用同一个 Release（内含三份产物）。
> 检测逻辑走 `/releases/latest`，tag 前缀（`v` / `desktop-v` / `android-v`）都会被正确剥离比较；
> 但若为单端单独打 tag 发 Release，另一端会把该版本误判为自己的更新，故保持统一 tag。

客户端以 `tag_name` 做版本比较、以 Release 正文作为更新日志、以 `html_url` 作为跳转下载地址；
仓库尚无 Release 时检测按「暂无更新」静默处理。

---

### 6. 接口探测工具 (`tools/probe/`)

教务系统改版或需验证底层接口时，可直接运行逆向探测工具：

```bash
# 方式一：网页控制台（推荐，零依赖，浏览器内登录）
node tools/probe/server.mjs --open

# 方式二：命令行探测
node tools/probe/probe.mjs --user 你的学号
```

**探测工具亮点**：
- **模块调度揭示**：自动跟随 `accessModule.do` 302 重定向揭示真实功能路径。
- **参数收割补测**：自动从响应中提取 `studentId`、`cid` 等关键标识进行缺参补测。
- **自动隐私脱敏**：落盘前自动对学号、姓名、证件号、手机号等关键个人隐私进行星号脱敏。
- **零外部依赖**：仅需 Node.js ≥ 18。

---

## 📚 接口逆向文档 (`docs/api/`)

在深入代码前，推荐先阅读手写接口文档中心 [`docs/api/README.md`](./docs/api/README.md) 与全局总览 [`00-overview.md`](./docs/api/00-overview.md)：

| 文档 | 对应业务 | 关键接口与说明 |
| --- | --- | --- |
| [00-overview.md](./docs/api/00-overview.md) | 全局总览 | 架构、编码异构、会话失效机制、内部 ID 机制 |
| [01-auth.md](./docs/api/01-auth.md) | 认证与会话 | `getCaptcha.do` 验证码拉取、`j_acegi_security_check` 登录认证 |
| [02-score.md](./docs/api/02-score.md) | 成绩与 GPA | `studentOwnScore.do` 历年成绩、GPA 及排名 |
| [03-timetable.md](./docs/api/03-timetable.md) | 课程表 | `showTimetable.do` 学期课表、周次与单双周 |
| [04-program.md](./docs/api/04-program.md) | 培养方案 | `programTree.do` 课组层级、学分要求与毕业审核 |
| [05-exam.md](./docs/api/05-exam.md) | 考试日程 | `studentQueryAllExam.do` 考场安排、座号与时间 |
| [06-classroom.md](./docs/api/06-classroom.md) | 空教室查询 | `roomschedule*.jsdo` 教学楼自习教室空闲占用推算 |
| [07-calendar.md](./docs/api/07-calendar.md) | 校历与周次 | `viewCalendarInfo.do` 当前教学周、真实开学日期 |
| [08-profile.md](./docs/api/08-profile.md) | 学籍档案 | `showPersonalInfo.do` 个人学籍与学籍异动记录 |
| [09-notice.md](./docs/api/09-notice.md) | 教学公告 | `calendarViewList.do` 教务在线通知列表 |
| [10-course.md](./docs/api/10-course.md) | 课程查询接口 | `courseSearch.do` 课程库检索 |
| [11-selection.md](./docs/api/11-selection.md) | 选课相关 | 选课批次与选课状态提示（查询类） |
| [12-portal.md](./docs/api/12-portal.md) | 门户与上下文 | `studentContext.do`、`menu.do` 上下文与菜单树 |

---

## 🔒 隐私、安全与合规声明

1. **隐私安全至上**：
   - 网页客户端采用纯前端直连与本地缓存架构，所有会话凭证（Cookie）及个人信息**仅保存在用户本地浏览器内**，绝不收集或上传到任何第三方服务器。
   - Android 客户端同样为直连架构：会话 Cookie 由本地 `CookieJar` 持有，教务数据落盘于应用私有目录（Room 数据库），登录态与偏好存于 DataStore，**不请求任何第三方服务**，卸载应用即清除全部本地数据。
   - 探测工具默认启用自动脱敏逻辑，测试数据落盘前自动屏蔽敏感隐私字段。
2. **只读保护原则**：
   - 本项目核心目标为**改善信息查询与日程体验**。
   - 任何涉及修改教务数据的高风险写操作（如选课、退课、修改学籍等）均不在客户端内提供，请登录官方教务系统原站办理。
3. **友好访问原则**：
   - 接口请求均采用防抖与按需加载策略，配合本地持久化缓存，有效减少对学校教务服务器的不必要请求与访问负担。

---

## 🛠️ 技术栈

### Web 端 (`web/`)

- **框架**：Vue 3 (Composition API / `<script setup>`)
- **构建工具**：Vite 5
- **样式方案**：Tailwind CSS v4
- **工具链**：Node.js 原生 ES Modules
- **持久化**：存储门面 `services/storage.js` 按环境自动选择——Web 版 localStorage /
  油猴版 GM 扩展存储（`GM_getValue` 等，清理站点数据不丢）/ 桌面版 Rust 后端
  落盘 `storage.json`（经本地服务 `/__app/storage` 端点）；承载教务数据缓存 + 会话与时间戳

### Android 端 (`android/`)

- **语言**：Kotlin 2.3.21（Java 21 目标）
- **UI**：Jetpack Compose（Compose BOM 2026.09）+ Material 3 + Material You 动态取色
- **导航与自适应**：Navigation Compose 2.10 + Material 3 Adaptive（`ListDetailPaneScaffold` 双栏成绩页）
- **网络与解析**：OkHttp 4.12（自定义 `CookieJar` 托管 JSESSIONID）+ Jsoup 1.18（HTML 解析，GBK / UTF-8 双解码自适应）
- **本地持久化**：Room 2.8（课表 / 成绩 / 考试 / 档案 / 培养方案 / 公告 分表缓存）+ DataStore Preferences（登录态、时间戳、主题偏好）
- **序列化与并发**：kotlinx.serialization 1.7 + kotlinx.coroutines 1.9
- **构建**：AGP 9.3 · `compileSdk 37` / `minSdk 30`（Android 11+）/ `targetSdk 36` · Release 启用 R8 代码与资源压缩
- **测试**：JUnit4 + kotlinx-coroutines-test（JVM 用例：解析器对抗性用例、Cookie 会话、GBK/UTF-8 解码、五分制绩点、学位选修规则、课程时间与学分统计边界）

### 桌面端 (`desktop-tauri/`)

- **容器**：Tauri 2（Rust 主进程 + 系统 WebView2）
- **内置服务**：仅监听 `127.0.0.1` 的本地反向代理，静态托管 `web/dist`


### 显示与账号切换

概览统计卡片按内容区域的实际宽度排列，适配高 DPI 和浏览器缩放。“GPA 计算说明”可展开查看五分制换算、加权公式、两门通识选修、0 学分排除及补考重修口径。

切换账号会先结束旧会话并重新取得验证码；客户端核对学校返回的学号后才确认登录。旧账号缓存会清理，尚未完成的旧同步结果不再写回新账号。学号输入示例为 `2401234567`。
