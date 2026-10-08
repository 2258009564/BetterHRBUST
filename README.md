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

自制的现代化哈尔滨理工大学教务在线（URP）客户端：Web / 油猴 / Windows 桌面端 / Android 原生四端同源。

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

- **100% 真实教务直连**：不使用任何模拟数据，数据全部直连哈理工教务在线系统，保障准确性。
- **独立现代化登录**：简洁高效的单列登录面板，支持验证码实时刷新、学号记住与会话快速切换。
- **登录即全量同步 · 之后只读本地**：登录成功后一次性拉取全部教务数据并持久化；日常打开与切换页面只读本地缓存，**不再自动请求教务**。仅在「每天首次打开」与「手动刷新」时联网，刷新入口位于顶栏（Android 为「更多」页首行）。
- **离线持久化与柔性会话提示**：教务 Session 失效或断网时自动无缝加载本地缓存；会话失效提示按周节流——刚登录一周内不打扰，仅在登录页告知已失效，手动刷新则要求重新登录。
- **概览工作台**：
  - 今日课程智能展示（课表大节加粗呈现，空闲状态智能提示）
  - 近期考试速览（智能时间过滤：未来考试与过去 7 天内考试）
  - 学业核心指标（已获得学分 / 培养方案总学分、平均学分绩点 GPA、挂科门数），其中已获得学分与培养方案页**同口径**（同一门课只计一次、选修课不计入），并附学位绩点 / 补考重修 / 挂科学分 / 提前毕业四项特色算法摘要
- **智能课程表**：支持按周次切换、当前周高亮、单双周过滤、节次时段分布与课程详情。
- **成绩与 GPA 分析**：历年成绩汇总、按学期/课程性质筛选、不及格标记；采用**哈理工官方五分制绩点**（60 分 = 1 绩点，90—100 分 = 4.0—5.0；五级制 优秀 4.5 / 良好 3.5 / 中等 2.5 / 及格 1.5），重修与补考记录自动合并去重。
- **学业统计仅统计必修课**：GPA、加权平均分、优秀率、不及格门数、累计挂科学分与学业风险预警**全部只统计必修课**，限选与任选课不参与任何计算（仅在成绩明细列表中展示），避免选修课拉低或虚高学业指标。
- **特色学业算法**：除标准 GPA 外，另提供**学位证算法**（必修课平均学分绩点 ≥ 1.5 的达标判定）、**推免资格自检**（必修课全部合格、补考重修累计 ≤ 2 门）、**学业风险预警**（必修课累计挂科学分判定留降级 / 退学）、**提前毕业判定**（必修课平均学分绩点 ≥ 4.0），阈值以学校教务处口径为准。
- **培养方案与学分进度**：与教务培养方案深度对齐，直观展示各课组学分要求与已获进度。
- **考试日程与倒计时**：清晰掌握考场、座号、时间及考试倒计时。
- **空教室检索**：按校区、教学楼、周次推算空闲自习教室。
- **学籍档案与全校课程**：学籍关键信息一览，全校开课名录便捷检索。
- **原生 Android 客户端**：Kotlin + Jetpack Compose 原生实现，Material You 动态取色与深浅色主题、边到边全屏 + 悬浮胶囊导航坞、自适应双栏成绩页（列表 / 明细）、下拉刷新；Room 本地持久化 + DataStore 会话偏好，离线同样可用。
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

Kotlin + Jetpack Compose 编写的原生 Android 客户端，与 Web 端共用同一套业务口径（接口路径、HTML 解析规则、五分制绩点与特色算法、缓存与刷新策略）。

**环境要求**：Android Studio（或 JDK 21）+ Android SDK；运行设备 **Android 11（API 30）及以上**。

```bash
cd android

# 构建 Debug 安装包（产物：app/build/outputs/apk/debug/app-debug.apk）
./gradlew :app:assembleDebug

# 运行单元测试（JVM，61 个用例）
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

**客户端特性**：

| 模块 | 说明 |
| --- | --- |
| 登录 | 学号 / 密码 / 验证码，支持验证码点击刷新；会话 Cookie 由自定义 `CookieJar` 托管 |
| 概览 | 学生信息卡（姓名、学号、学院专业、当前教学周）、五分制 GPA / 已获学分 / 未通过门数、特色算法摘要、近期考试倒计时、今日课程、功能九宫格 |
| 课程表 | 周次滑杆切换、按天查看、单双周过滤、课程详情底部弹窗 |
| 成绩 | **自适应双栏**（平板 / 横屏为列表 + 明细并排）、课程搜索、选课属性与及格状态筛选、补考重修合并标记、成绩明细 |
| 考试 | 近期考试倒计时轮播 + 全部考试清单 |
| 培养方案 | 毕业进度总览、各课组「已获 / 要求」学分对照、课组内课程展开 |
| 空教室 / 全校课程 / 学籍档案 | 教室占用推算、开课名录检索、学籍基本信息与异动记录 |
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
教务会话 Cookie 持久化于 WebView2 用户数据目录。体量轻量：安装包 ≤ 10MB、
单 exe 绿色版（前端资产嵌入二进制）、主进程为 Rust 实现（无 Node 运行时）、
渲染采用系统 WebView2，并有 mock 教务系统的 Rust 集成测试完整覆盖。

```powershell
cd desktop-tauri
npm install
npm run dist    # 构建 web + 打包，产物见 desktop-tauri/release/ 与 bundle/nsis/
```

架构、体积/内存目标、开发与分发细节见
[`desktop-tauri/README.md`](./desktop-tauri/README.md)。

---

### 5. 接口探测工具 (`tools/probe/`)

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
| [10-course.md](./docs/api/10-course.md) | 全校课程名录 | `courseSearch.do` 课程库检索 |
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
- **持久化**：localStorage（教务数据缓存 + 会话与时间戳）

### Android 端 (`android/`)

- **语言**：Kotlin 2.3.21（Java 21 目标）
- **UI**：Jetpack Compose（Compose BOM 2026.09）+ Material 3 + Material You 动态取色
- **导航与自适应**：Navigation Compose 2.10 + Material 3 Adaptive（`ListDetailPaneScaffold` 双栏成绩页）
- **网络与解析**：OkHttp 4.12（自定义 `CookieJar` 托管 JSESSIONID）+ Jsoup 1.18（HTML 解析，GBK / UTF-8 双解码自适应）
- **本地持久化**：Room 2.8（课表 / 成绩 / 考试 / 档案 / 培养方案 / 公告 分表缓存）+ DataStore Preferences（登录态、时间戳、主题偏好）
- **序列化与并发**：kotlinx.serialization 1.7 + kotlinx.coroutines 1.9
- **构建**：AGP 9.3 · `compileSdk 37` / `minSdk 30`（Android 11+）/ `targetSdk 36` · Release 启用 R8 代码与资源压缩
- **测试**：JUnit4 + kotlinx-coroutines-test（61 个 JVM 用例：解析器对抗性用例、Cookie 会话、GBK/UTF-8 解码、五分制绩点与四项特色算法边界）

### 桌面端 (`desktop-tauri/`)

- **容器**：Tauri 2（Rust 主进程 + 系统 WebView2）
- **内置服务**：仅监听 `127.0.0.1` 的本地反向代理，静态托管 `web/dist`
