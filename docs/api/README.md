# 哈理工教务在线 · 接口文档

哈尔滨理工大学教务在线（<http://jwzx.hrbust.edu.cn/academic/>）的接口说明，
用于指导客户端开发与后续维护。

文档按功能域拆分，每篇包含该模块的接口清单、请求参数、响应结构与解析要点。

---

## 开始之前

如果你只读一篇，请读 [**`00-overview.md`**](./00-overview.md)。里面有四个必须先知道的事实：

1. **编码不统一** —— 成绩模块是 UTF-8，课表/菜单是 GBK，必须按响应头判断；
2. **接口地址猜不出来** —— 菜单只给 `moduleId`，真实地址要靠 `accessModule.do` 调度揭示；
3. **会话失效不返回 401** —— 而是返回登录页 HTML，且要用成规模的回落来判断；
4. **上下文 ID 不是学号** —— 课表和成绩接口用的是教务内部 ID。

跳过这些直接动手，几乎一定会在这四点上卡住。

## 文档导航

| 文档 | 内容 |
| --- | --- |
| [`00-overview.md`](./00-overview.md) | **必读**：系统架构、编码、会话、上下文参数、模块调度、通用约定与错误处理 |
| [`01-auth.md`](./01-auth.md) | 认证与会话、菜单获取、门户页与框架结构、模块调度、DWR |
| [`02-scores.md`](./02-scores.md) | 成绩查询（全部成绩、分学期成绩、学年学期列表） |
| [`03-timetable.md`](./03-timetable.md) | 课表（大节课表、基础课表、周次课表）、个人教学计划 |
| [`04-student.md`](./04-student.md) | 个人信息、学籍信息、学生照片、学籍异动、级联下拉 |
| [`05-exam.md`](./05-exam.md) | 考试安排（近期考试、全部考试列表与分页） |
| [`06-course.md`](./06-course.md) | 课程查询、课程详情、教师信息 |
| [`07-classroom.md`](./07-classroom.md) | 教室时间占用查询 |
| [`08-elective.md`](./08-elective.md) | 选课入口与课节分组 |
| [`09-notice.md`](./09-notice.md) | 教学运行公告、校历、课程通知、消息已读 |
| [`10-eva.md`](./10-eva.md) | 教学评价结果与教学报告 |
| [`11-account.md`](./11-account.md) | 修改密码、等级考试、重修重考报名 |

## 接口总览

| 模块 | 接口数 | 主要接口 |
| --- | ---: | --- |
| 认证与框架 | 21 | `getCaptcha.do`、`j_acegi_security_check`、`listLeft.do`、`accessModule.do` |
| 成绩查询 | 4 | `manager/score/studentOwnScore.do` |
| 课表与教学计划 | 6 | `manager/coursearrange/showTimetable.do` |
| 个人信息与学籍 | 11 | `showPersonalInfo.do`、`student/studentinfo/studentInfoModifyIndex.do` |
| 考试安排 | 3 | `student/exam/index.jsdo`、`manager/examstu/studentQueryAllExam.do` |
| 课程与教师 | 5 | `manager/querycourse/index.jsdo` |
| 教室查询 | 3 | `teacher/teachresource/roomschedulequery.jsdo` |
| 选课 | 3 | `manager/electcourse/elective.do` |
| 公告与校历 | 5 | `calendarinfo/viewCalendarInfo.do`、`calendar/calendarViewList.do` |
| 教学评价 | 2 | `eva/index/resultlist.jsdo` |
| 账户与其它 | 2 | `sysmgr/user_password.jsdo` |
| **合计** | **65** | |

## 文档约定

**路径写法**：文中所有路径都省略了基址，实际请求需要拼接：

```
/academic/manager/score/studentOwnScore.do
└──────┘└───────────────────────────────┘
  基址              文档中的写法
```

**接口标识**：路径以 `.do` / `.jsdo` / `.jsp` / `.htm` 结尾。注意
**`.jsdo` 返回的是 HTML 片段，不是 JSON**，命名有误导性。

**编码**：文档中每个接口都标注了实际编码。**不要写死 GBK**——
教务系统不同模块编码不同，详见 [`00-overview.md` §3](./00-overview.md)。

**示例数据**：文中的示例值（学号、内部 ID、课程名等）来自真实响应，
仅用于说明格式。实际使用时这些值都需要从接口动态获取。

## 如何使用本文档

### 排查某个接口为什么没数据

按顺序检查这四项：

| 检查项 | 症状 | 参见 |
| --- | --- | --- |
| 编码判断 | 中文乱码、关键字匹配失败 | [§3 编码](./00-overview.md) |
| 上下文参数 | 返回 0 字节或错误页 | [§4 上下文](./00-overview.md) |
| 会话状态 | 返回登录页 HTML | [§2 会话](./00-overview.md) |
| 请求方法 | POST 接口用 GET 调用 → 404 或错误页 | 各模块文档 |

### 验证接口是否可用

仓库内的探测工具可以直接验证任意接口：

```bash
# 网页控制台：浏览器里登录，服务端持有会话
node tools/probe/server.mjs --open

# 命令行：只探测某个模块
node tools/probe/probe.mjs --only score.*

# 只探测单个接口
node tools/probe/probe.mjs --only score.byTerm

# 离线重分析已有样本（无需联网）
node tools/probe/probe.mjs --analyze-only
```

探测结果保存在 `tools/probe/output/`：

| 产物 | 内容 |
| --- | --- |
| `manifest.json` | 每个接口的请求结果（状态码、编码、大小） |
| `manifest.md` | 同上，可读版本 |
| `analysis.md` | **样本结构分析**：每个页面解析出的表头、表单字段、嵌套地址 |
| `decoded/` | 按正确字符集解码后的 UTF-8 副本，可直接阅读 |
| `samples/` | 服务器原始响应字节 |
| `portal/` | 登录后门户页的多层框架抓取与分析 |

新增接口后，把它补进 `tools/probe/catalog.mjs`，就能被工具持续验证。
详见 [`tools/probe/README.md`](../../tools/probe/README.md)。

> ⚠️ **探测样本包含隐私数据**（成绩单、身份证号、手机号、家庭住址）。
> `output/` 已配置 gitignore，但请勿分享该目录，也不要把样本上传到公开位置。

## 已知限制

| 限制 | 说明 |
| --- | --- |
| 仅 HTTP | 无 HTTPS，浏览器环境需处理混合内容限制 |
| 学期切换受限 | 只能拿到「当前学年学期」的内部序号，历史学期数据需靠 `year`/`term` 参数 |
| 部分接口需参数 | 级联下拉类接口直接请求会返回「提示信息」错误页 |
| 教师课表不可用 | 学生账号请求 `timetableType=TEACHER` 返回无权限错误页 |
| 加密参数 | 部分接口的 `studentId`/`userid` 是加密串，必须从页面中提取，不能自拼 |
| 无官方文档 | 本文档内容全部来自对实际系统的分析与验证 |

## 贡献

修正文档时请同时更新对应的 `tools/probe/catalog.mjs` 条目
（路径、参数、说明），保持文档与探测工具一致。
