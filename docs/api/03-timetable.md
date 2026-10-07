# 03 · 课表与教学计划

课表由 `manager/coursearrange/showTimetable.do` 提供，通过 `sectionType`
参数区分「大节课表」与「小节课表」。本模块还包含周次课表与个人教学计划。

---

## 接口清单

| 接口 | 方法 | 路径 | 说明 |
| --- | --- | --- | --- |
| 学生课表（大节） | GET | `manager/coursearrange/showTimetable.do` | `sectionType=COMBINE` |
| 学生课表（小节） | GET | `manager/coursearrange/showTimetable.do` | `sectionType=BASE` |
| 周次课表 | GET | `manager/coursearrange/studentWeeklyTimetable.do` | 按周展示 |
| 个人教学计划（入口） | GET | `manager/studyschedule/studentSelfSchedule.jsdo` | 提供跳转链接 |
| 教学计划跳转器 | GET | `manager/studyschedule/scheduleJump.jsp` | 仅跳转，无数据 |
| 培养方案（按学期） | GET | `manager/studyschedule/studentScheduleShowByTerm.do` | **数据最完整** |
| 培养方案（按课程线） | GET | `manager/studyschedule/studentScheduleLineShow.do` | 同源另一视图 |
| 培养方案（按学期排序） | GET | `manager/studyschedule/studentScheduleCourseTermOrder.do` | 精简视图 |
| 培养方案（框架页） | GET | `manager/studyschedule/studentScheduleShowFrame.do` | frameset 容器 |
| 教学计划说明 | GET | `manager/studyschedule/help.htm` | 模块默认落点 |

---

## 1. 学生课表

```
GET /academic/manager/coursearrange/showTimetable.do
      ?id={studentId}&yearid={year}&termid={term}
      &timetableType=STUDENT&sectionType={COMBINE|BASE}
```

### 1.1 请求参数

| 参数 | 必需 | 说明 |
| --- | --- | --- |
| `id` | 是 | **教务内部学生 ID**，不是学号 |
| `yearid` | 是 | 学年序号，如 `46` |
| `termid` | 是 | 学期序号，`1` = 春，`2` = 秋 |
| `timetableType` | 是 | 固定 `STUDENT` |
| `sectionType` | 是 | `COMBINE` = 大节课表，`BASE` = 小节课表 |

前三个参数来自 [`00-overview.md` §4](./00-overview.md) 的上下文。

> ⚠️ `id` 填学号会得到空课表。必须用 `currcourse.jsdo` 返回的内部 ID。

### 1.2 sectionType 的区别

| 取值 | 含义 | 页面上的名称 |
| --- | --- | --- |
| `BASE` | 小节课表，每个小节独立显示 | 小节课表 |
| `COMBINE` | 大节课表，连续小节合并显示 | **大节课表** |

教务页面自身使用的切换链接是：

```html
<a class="infolist"
   href="?id=100001&yearid=46&termid=2&timetableType=STUDENT&sectionType=COMBINE">
  大节课表
</a>
```

两者课程数据一致，只是**节次合并规则不同**。推荐用 `BASE`：
它保留了每节课的独立位置，解析更简单、信息更完整。

### 1.3 响应

| 项目 | 值 |
| --- | --- |
| 状态 | `200` |
| 编码 | **GBK** |
| 标题 | 学生课表 |
| 大小 | 约 8～12 KB |

### 1.4 响应结构

课表是一张二维表格：

```html
<table id="timetable" class="infolist_hr">
  <tr>
    <th>&nbsp;</th>
    <th nowrap>周一</th><th>周二</th>…<th>周日</th>
  </tr>
  <tr class="infolist_hr_common">
    <th nowrap>第1节<br></th>
    <td id="1-1" class="center">&nbsp;</td>
    <td id="2-1" class="center">&nbsp;</td>
    …
  </tr>
  …
</table>
```

| 元素 | 含义 |
| --- | --- |
| `<table id="timetable" class="infolist_hr">` | 课表容器 |
| 表头 `<th>周一</th>…<th>周日</th>` | 星期，共 7 列 |
| 行 `<tr class="infolist_hr_common">` | 一个节次 |
| 行首 `<th>第N节<br></th>` | 节次标签 |
| 单元格 `<td id="{星期}-{节次}" class="center">` | **`id` 直接给出坐标** |
| 空单元格 | 内容为 `&nbsp;` |

`id` 的格式是 `{星期}-{节次}`，从 1 开始计数：

```
id="1-1"   → 周一 第1节
id="3-5"   → 周三 第5节
id="7-12"  → 周日 第12节
```

课表共 **12 节 × 7 天 = 84 个单元格**。

### 1.5 单元格内容格式

非空单元格的内容是一条以 `<br>` 分隔的记录：

```
<<课程名>>;课序号
上课地点
任课教师
上课周次
学时类型
```

真实示例（HTML 转义前后对照）：

```html
<!-- 原始 HTML -->
<td id="3-1" class="center">&lt;&lt;算法设计与分析&gt;&gt;;2<br>西-新A306<br>张三B<br>4-10周<br>讲课学时</td>

<!-- 解码后 -->
<td id="3-1" class="center"><<算法设计与分析>>;2<br>西-新A306<br>张三B<br>4-10周<br>讲课学时</td>
```

各字段：

| 顺序 | 内容 | 示例 | 说明 |
| --- | --- | --- | --- |
| 1 | `<<课程名>>;课序号` | `<<算法设计与分析>>;2` | 课名在 `<<` `>>` 之间，分号后是课序号 |
| 2 | 上课地点 | `西-新A306` | `校区-楼栋房间` |
| 3 | 任课教师 | `张三B` | 末尾字母为教学班标识，可能没有 |
| 4 | 上课周次 | `4-10周` | 可能是 `1-4周`、`1-18周` 等 |
| 5 | 学时类型 | `讲课学时` | 也可能是实验学时、上机学时等 |

解析示例：

```js
function parseCourseCell(html) {
  const text = html.replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&nbsp;/g, ' ');
  if (!text.trim()) return null;

  const lines = text.split(/<br\s*\/?>/i).map((s) => s.trim()).filter(Boolean);
  if (lines.length === 0) return null;

  // 第一行：<<课程名>>;课序号
  const head = lines[0].match(/<<(.+?)>>\s*;?\s*(\d+)?/);
  const courseName = head ? head[1] : lines[0];
  const courseSeq = head && head[2] ? head[2] : '';

  return {
    courseName,
    courseSeq,
    location: lines[1] || '',
    teacher: (lines[2] || '').replace(/[A-Za-z]$/, ''),  // 去掉教学班标识
    weekRange: lines[3] || '',
    hourType: lines[4] || ''
  };
}
```

> **防御性解析**：正常情况下每个单元格最多一门课。但 URP 在课程冲突时
> 可能把多门课拼接在同一单元格内，建议用全局匹配取出所有 `<<...>>` 片段，
> 而不是只用第一个匹配结果。

### 1.6 分析周次

`上课周次` 字段需要解析成周次数组，用于「查看第 N 周」功能：

```js
/**
 * 解析周次表达式
 * '1-16周'      → [1..16]
 * '1-4周'       → [1..4]
 * '1-16周(单)'  → [1,3,5,...,15]
 * '1-16周(双)'  → [2,4,6,...,16]
 * '3,5,7周'     → [3,5,7]
 */
function parseWeekRange(expr) {
  const weeks = new Set();
  const isOdd = /单/.test(expr);
  const isEven = /双/.test(expr);

  for (const part of expr.match(/\d+(?:-\d+)?/g) || []) {
    const [start, end] = part.split('-').map(Number);
    const from = start;
    const to = end || start;

    for (let w = from; w <= to; w += 1) {
      if (isOdd && w % 2 === 0) continue;
      if (isEven && w % 2 === 1) continue;
      weeks.add(w);
    }
  }
  return [...weeks].sort((a, b) => a - b);
}
```

---

## 2. 周次课表

```
GET /academic/manager/coursearrange/studentWeeklyTimetable.do?yearid={year}
```

| 参数 | 必需 | 说明 |
| --- | --- | --- |
| `yearid` | 是 | 学年序号 |

在 `currcourse.jsdo` 页面中以「周次课表」按钮的形式出现。

> ⚠️ **只带 `yearid` 会返回「提示信息」错误页**（实测），该接口还需要
> 其它参数（具体未知，教务页面上是通过表单提交进入的）。
> 做「按周查看」时，更可靠的做法是用 `showTimetable.do` 的周次字段
> 在客户端自行筛选，而不是调用这个接口。

---

## 3. 个人教学计划

```
GET /academic/manager/studyschedule/studentSelfSchedule.jsdo
```

| 项目 | 值 |
| --- | --- |
| 状态 | `200` |
| 标题 | 个人教学计划 |
| 大小 | 约 4.8 KB |

响应中包含 4 个 `scheduleJump.jsp` 跳转链接，指向培养方案的四种展示形态：

| 目标 | 说明 |
| --- | --- |
| `studentScheduleLineShow` | 按课程线展示 |
| `studentScheduleShowFrame` | 框架展示 |
| `studentScheduleCourseTermOrder` | 按学期排序 |
| `studentScheduleShowByTerm` | 按学期展示 |

> ⚠️ 链接中的 `studentId` 是**加密串**，不是明文内部 ID。
> 必须从页面响应中提取并原样回传，不能自己拼接。
> 详见 [`00-overview.md` §4.6](./00-overview.md)。

---

## 4. 教学计划跳转器

```
GET /academic/manager/studyschedule/scheduleJump.jsp
      ?link={目标}&studentId={加密串}&classId={班级ID}
```

| 参数 | 必需 | 说明 |
| --- | --- | --- |
| `link` | 是 | 目标接口名，如 `studentScheduleShowByTerm.do` |
| `studentId` | 是 | 加密串，从 `studentSelfSchedule.jsdo` 中提取 |
| `classId` | 否 | 班级 ID |

**这个接口本身没有数据**——响应体只有一句
`location.href="studentScheduleShowByTerm.do?z=z&studentId=...&classId=..."`
（约 480 字节），作用是把浏览器带去真正的功能页。

它的价值在于**揭示了真实目标地址与完整参数**。调用后应解析其中的
`location.href`，再去请求 §5 的真实页面。

---

## 5. 个人培养方案

培养方案数据在 `manager/studyschedule/` 下，有四种展示形态。
**真实地址无法推测**——按 URP 命名猜测的 `manager/teachingplan/*`、
`manager/studentcredit/*` 全部 404，只能通过上面的跳转链路发现：

```
studentSelfSchedule.jsdo           ← 入口页（§3）
  └─ scheduleJump.jsp?link=...     ← 跳转器（§4，揭示目标与参数）
       └─ studentSchedule*.do      ← 真实的培养方案页面
```

| 接口 | 大小 | 说明 |
| --- | ---: | --- |
| `studentScheduleShowByTerm.do` | **约 442 KB** | 按学期展示，**数据最完整** |
| `studentScheduleLineShow.do` | 约 312 KB | 按课程线展示 |
| `studentScheduleCourseTermOrder.do` | 约 94 KB | 按学期排序的精简视图 |
| `studentScheduleShowFrame.do` | 约 10 KB | frameset 容器 |

### 5.1 请求

```
GET /academic/manager/studyschedule/studentScheduleShowByTerm.do
      ?z=z&studentId={加密串}&classId={班级ID}
```

| 参数 | 必需 | 说明 |
| --- | --- | --- |
| `z` | 是 | 固定 `z`（跳转链接里的写法） |
| `studentId` | 是 | **加密串**，从入口页提取 |
| `classId` | 是 | 班级 ID，同样从跳转链接里取 |

### 5.2 响应结构

以 `ShowByTerm`（442KB）为例，页面分三部分：

**① 头部信息表**（class `datalist`）：

```
院系 | 专业 | 年级 | 学生类别 | 专业方向 | 最后修改时间 | 查询成绩时间
```

**② 课组要求**（`<option>` 列表）——这是**毕业学分结构**：

```
第二课堂（2023）        选课属性：必修  学分要求=2.0   门数要求=2
公共外语（小语种（2023） 选课属性：必修  学分要求=49.5  门数要求=15
人文 、社科类（2023）    选课属性：必修  学分要求=35.0  门数要求=20
实践性教学环节（2023）   选课属性：必修  学分要求=42.0  门数要求=12
学科基础课程（2023）     选课属性：必修  学分要求=26.5  门数要求=8
专业必修课（2023）       选课属性：必修  学分要求=17.0  门数要求=8
自然科学类（2023）       选课属性：必修  学分要求=29.0  门数要求=8
专业选修课（2023）       选课属性：限选  学分要求=8.0   门数要求=4
```

每个课组的文本格式固定为
`{课组名}（{年级}） 选课属性：{属性} 学分要求={学分} 门数要求={门数}`，可正则提取。

**③ 课程明细**（class `datalist`，按学年学期分组）：

```
课程号 | 课程名 | 考核方式 | 学分 | 学时 | 课程类别 | 课组 | 课组要求 | 专业方向
```

示例数据行：

```
U960123dW10N1 | 国家安全教育（网络） | 分散（55） | 1 | 16 | 非学位课 | 第二课堂（2023） | 必修
```

行按「第 N 学年 / 学年学期」分组展示，解析时需要跟踪分组标记。

### 5.3 与成绩关联

培养方案给出「要求」，成绩给出「实际」，两者结合可做**学分完成度**分析：

| 数据 | 来源 | 用途 |
| --- | --- | --- |
| 课组学分要求 | 本模块（§5.2 ②） | 毕业要求 |
| 已获学分（按课组） | [`02-scores.md`](./02-scores.md) | 实际完成 |
| 课程归属哪个课组 | 本模块（课程明细的「课组」列） | 关联键 |

### 5.4 注意事项

- 页面很大（数百 KB），**不要在移动端全量渲染**，按学期或课组拆分展示；
- `studentId` 加密串随会话变化，**每次都要重新提取**，不能跨会话缓存；
- 四种视图数据同源，选 `ShowByTerm` 一种即可，不必全部请求。

---

## 5. 导出日历（ICS）

课表可以导出为 iCalendar 格式供手机日历订阅。关键点是**开学日期**——
教务系统不直接提供，有两种获取途径：

### 5.1 由当前教学周反推

`listLeft.do` 返回当前教学周（如「第 6 周」），据此反推：

```js
function estimateTermStartDate(currentWeek, reference = new Date()) {
  // 找到本周周一
  const monday = new Date(reference);
  const day = monday.getDay();                       // 0 = 周日
  monday.setDate(monday.getDate() + (day === 0 ? -6 : 1 - day));
  monday.setHours(0, 0, 0, 0);

  // 向前回退 (当前周 - 1) 周
  monday.setDate(monday.getDate() - (currentWeek - 1) * 7);
  return monday;
}
```

### 5.2 由校历获取

`calendar/calendarViewList.do` 提供校历，可得到更精确的开学日期。
详见 [`09-notice.md`](./09-notice.md)。

### 5.3 生成事件

每门课按「星期 + 节次 + 周次」生成周期性事件：

```js
// 上课时间表（第 N 节 → 起止时间）
const TIME_SLOTS = {
  1:  ['08:10', '08:55'],
  2:  ['09:00', '09:45'],
  3:  ['10:05', '10:50'],
  4:  ['10:55', '11:40'],
  5:  ['13:30', '14:15'],
  6:  ['14:20', '15:05'],
  7:  ['15:25', '16:10'],
  8:  ['16:15', '17:00'],
  9:  ['18:00', '18:45'],
  10: ['18:50', '19:35'],
  11: ['19:45', '20:30'],
  12: ['20:35', '21:20']
};
```

**上述时间仅为常见作息，不同学期可能调整，建议做成可配置项。**

生成事件时注意：

- 事件必须有 `DTSTART` 和 `DTEND`，缺少会导致 `.ics` 文件不符合规范；
- 单双周用 `RRULE` 的 `INTERVAL=2` 描述；
- 非连续周次（如 `1-4周` 和 `8-12周` 合并）需要拆成多个事件，
  或使用 `RDATE` 显式列出日期。

---

## 6. 实现建议

### 6.1 数据合并

课表本身不含教师姓名以外的信息。若需展示课程详情（学分、课程号），
需要与以下接口关联：

| 数据 | 来源 |
| --- | --- |
| 课程号、学分、选课属性 | `student/currcourse/currcourse.jsdo` |
| 课程详情 | `manager/querycourse/course_detail.jsdo?cid=` |
| 教师信息 | `manager/teacherinfo/showTeacherInfoItem.do?userid=` |

关联键是**课程名 + 课序号**（课表单元格里就有课序号）。

### 6.2 常见问题

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 课表为空 | `id` 传了学号 | 改用内部学生 ID |
| 课表为空 | `yearid`/`termid` 过期 | 重新取上下文 |
| 中文乱码 | 按 UTF-8 解码了（本接口是 GBK） | 按响应头 charset 解码 |
| 单元格解析错位 | 未处理 `&lt;` 转义 | 先反转义再按 `<br>` 切分 |
| 教师名末尾多字母 | 教学班标识 | 解析时去掉 |

### 6.3 展示建议

- 默认定位到当前教学周（来自 `listLeft.do`）；
- 支持按周筛选，用单元格里的周次字段过滤；
- 周六周日的列在无课时可隐藏，避免宽屏浪费空间。
