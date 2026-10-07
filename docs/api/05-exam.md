# 05 · 考试安排

考试数据有两个接口：`student/exam/index.jsdo` 返回近期考试（默认视图），
`manager/examstu/studentQueryAllExam.do` 返回全部考试并支持排序与分页。

**注意这两个接口的编码不同**：前者 GBK，后者 UTF-8。

---

## 接口清单

| 接口 | 方法 | 路径 | 说明 |
| --- | --- | --- | --- |
| 近期考试 | GET | `student/exam/index.jsdo` | 默认视图，含 7 天内考试 |
| 全部考试入口 | POST | `manager/examstu/studentQueryAllExamPre.do` | 详情页引导页 |
| 全部考试列表 | GET | `manager/examstu/studentQueryAllExam.do` | 支持排序与分页 |

---

## 1. 近期考试

```
GET /academic/student/exam/index.jsdo
```

### 1.1 请求

| 参数 | 说明 |
| --- | --- |
| 无 | 直接请求即可 |

### 1.2 响应

| 项目 | 值 |
| --- | --- |
| 状态 | `200` |
| 编码 | **GBK** |
| 标题 | 学生考试安排 |
| 大小 | 约 1.8 KB |

### 1.3 响应结构

```html
<table cellpadding="0" cellspacing="0" class="infolist_tab">
  <tr>
    <th>课程号</th>
    <th>课程名称</th>
    <th>考试时间</th>
    <th>考试地点</th>
    <!--<th>考试方式</th>-->
    <th>考试性质</th>
  </tr>
  <tr class="infolist_common">
    <td>U010203TW04W5</td>
    <td>复变函数与积分变换</td>
    <td>2026-01-15 08:10--09:50</td>
    <td>西-新A308&nbsp;</td>
    <!--<td></td>-->
    <td>开班重修</td>
  </tr>
</table>
```

| 列 | 说明 | 示例 |
| --- | --- | --- |
| 课程号 | 教学班编码 | `U010203TW04W5` |
| 课程名称 | | `复变函数与积分变换` |
| 考试时间 | `YYYY-MM-DD HH:mm--HH:mm` | `2026-01-15 08:10--09:50` |
| 考试地点 | `校区-楼栋房间` | `西-新A308` |
| 考试性质 | | `开班重修` |

### 1.4 解析要点

**① 必须剥离 HTML 注释，否则列会错位。**

这一页的注释陷阱非常典型：`考试方式` 列被注释掉了，**同一行里对应的
`<td>` 也被注释掉**：

```html
<!--<th>考试方式</th>-->          ← 表头被注释
<!--<td></td>-->                 ← 数据单元格也被注释
```

如果不剥离注释：

- 表头会解析出 6 列（含「考试方式」），但数据行只有 5 个 `<td>`；
- 按固定索引取「考试性质」会取到空值或错位。

```js
// 解析前必须执行
const clean = html.replace(/<!--[\s\S]*?-->/g, '');
```

**② 按表头文字动态映射列，不要写死索引。**

```js
// 剥离注释后取表头
const headers = [...doc.querySelectorAll('table.infolist_tab th')]
  .map((th) => th.textContent.trim());

const COL = {
  courseId:   headers.indexOf('课程号'),
  courseName: headers.indexOf('课程名称'),
  time:       headers.indexOf('考试时间'),
  location:   headers.indexOf('考试地点'),
  property:   headers.indexOf('考试性质')
};
```

**③ 数据行 class 是 `infolist_common`**（不是 `infolist_hr_common`）。

### 1.5 数据范围限制

页面底部的说明文字给出了数据范围：

```
这里列出7天前(2026-10-01)开始的全部考试安排。今天以前的考试模糊显示。
```

即**只返回 7 天前之后的考试**，且已过去的考试信息会被模糊处理。
需要完整数据请用 §2 的接口。

---

## 2. 全部考试安排

### 2.1 引导页

```
POST /academic/manager/examstu/studentQueryAllExamPre.do
```

在近期考试页面中以表单形式出现，无字段，直接提交：

```html
<form action="/academic/manager/examstu/studentQueryAllExamPre.do"
      method="post" target="_blank">
  <input type="submit" class="button" value="查看全部考试安排">
</form>
```

`target="_blank"` 说明教务系统是在新窗口打开。接口返回引导页，
实际数据由下一个接口提供。

> 注意用 **POST**。用 GET 请求会得到错误页或 404。

### 2.2 全部考试列表

```
GET /academic/manager/examstu/studentQueryAllExam.do
      ?pagingNumberPerVLID=10
      &sortColumnVLID=executionPlan.course.pcourseid
      &pagingPageVLID=1
      &sortDirectionVLID=-1
```

| 参数 | 必需 | 默认 | 说明 |
| --- | --- | --- | --- |
| `pagingNumberPerVLID` | 否 | `10` | 每页条数 |
| `pagingPageVLID` | 否 | `1` | 页码 |
| `sortColumnVLID` | 否 | `executionPlan.course.pcourseid` | 排序列 |
| `sortDirectionVLID` | 否 | `-1` | `1` 升序 / `-1` 降序 |

### 2.3 响应

| 项目 | 值 |
| --- | --- |
| 状态 | `200` |
| 编码 | **UTF-8**（与上文的近期考试不同） |
| 标题 | 考试安排 |
| 大小 | 约 11 KB / 页 |

### 2.4 可排序列

表头是可点击的排序链接，从中可以直接读出所有可用排序列：

| 列名 | `sortColumnVLID` 取值 |
| --- | --- |
| 课程号 | `executionPlan.course.pcourseid` |
| 课程名称 | `executionPlan.course.courseName` |
| 考试时间 | `examRoom.exam.endTime` |
| 考试地点 | `examRoom.room.rname` |
| 考试性质 | `examProperty.name` |

这些字段路径同时也是后端实体结构的线索：
`executionPlan.course.*` 对应课程，`examRoom.exam.*` 对应考试时间，
`examRoom.room.*` 对应考场。

### 2.5 分页信息

分页区使用 URP 通用分页组件：

```html
<table class='classicLookPagingTag PagingTag'>
  <tr class='classicLookPagingTag PagingTag'>
    <td class='classicLookSummary Summary'>共<b>31</b>条，<b>1</b> / <b>4</b>页</td>
```

解析总条数：

```js
const summary = doc.querySelector('.classicLookSummary')?.textContent || '';
const [total, current, pages] = summary.match(/\d+/g).map(Number);
// total = 31, current = 1, pages = 4
```

### 2.6 数据行

**数据行的 class 是 `classicLook0`，不是 `infolist_*`：**

```html
<tr class="classicLook0">
  <td class="classicLook0">U090523TW01W3</td>
  …
</tr>
```

按 `infolist_common` 查找会取不到数据。建议按**表格容器**定位而不是按行 class：

```js
// 更稳妥：找到数据表后再取其中的 tr
const table = doc.querySelector('table.classicLook0') ||
              doc.querySelector('table[id]');
const rows = [...table.querySelectorAll('tr')].filter(
  (tr) => tr.querySelectorAll('td').length >= 4 && !tr.classList.contains('PagingTag')
);
```

### 2.7 翻页请求

页码通过 `pagingPageVLID` 控制，其他参数保持不变：

```
studentQueryAllExam.do?pagingNumberPerVLID=10&sortColumnVLID=executionPlan.course.pcourseid&pagingPageVLID=2&sortDirectionVLID=-1
```

也可以直接把 `pagingNumberPerVLID` 调到较大值一次取完（如 `100`），
减少请求次数。

---

## 3. 实现建议

### 3.1 接口选择

| 场景 | 推荐接口 |
| --- | --- |
| 首页展示「近期考试」 | `student/exam/index.jsdo` |
| 考试列表页 / 需要完整历史 | `manager/examstu/studentQueryAllExam.do` |

两个接口的数据格式不同，建议**统一成一个内部数据结构**后再交给 UI：

```js
{
  courseId: 'U010203TW04W5',
  courseName: '复变函数与积分变换',
  startTime: '2026-01-15T08:10:00',   // 归一化为 ISO 格式
  endTime:   '2026-01-15T09:50:00',
  location: '西-新A308',
  property: '开班重修'
}
```

`考试时间` 字段的原始格式是 `2026-01-15 08:10--09:50`，
注意分隔符是**两个短横线** `--`，不是单个连字符：

```js
function parseExamTime(raw) {
  const m = raw.match(/(\d{4}-\d{2}-\d{2})\s+(\d{2}:\d{2})--(\d{2}:\d{2})/);
  if (!m) return { startTime: null, endTime: null };
  return {
    startTime: `${m[1]}T${m[2]}:00`,
    endTime: `${m[1]}T${m[3]}:00`
  };
}
```

### 3.2 常见问题

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 中文乱码（近期考试） | 按 UTF-8 解码了 | 该接口是 GBK |
| 中文乱码（全部考试） | 按 GBK 解码了 | 该接口是 UTF-8 |
| 考试性质列为空 | 未剥离 HTML 注释导致列错位 | 解析前先剥注释 |
| 取不到数据行 | 用了 `infolist_common` 选择器 | 数据行是 `classicLook0` |
| 请求全部考试返回 404 | 用了 GET | 引导页必须用 POST |
| 看不到历史考试 | `index.jsdo` 只返回 7 天内的 | 改用 `studentQueryAllExam.do` |

### 3.3 展示建议

- 按考试时间排序，未考的在前，已考的折叠；
- 近期考试（3 天内）高亮提醒；
- 提供「添加到日历」功能，格式与课表导出一致（见 [`03-timetable.md` §5](./03-timetable.md)）。
