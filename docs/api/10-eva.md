# 10 · 教学评价

教学评价模块提供评价结果的查看，以及每门课的教学报告入口。

评价数据同样受时间窗口限制——只有完成了评价，结果列表才会有内容。

---

## 接口清单

| 接口 | 方法 | 路径 | 说明 |
| --- | --- | --- | --- |
| 评价结果列表 | GET | `eva/index/resultlist.jsdo` | 本人评价记录 |
| 教学报告 | GET | `teacher/teachingtask/schoolTeachingReportIndexStudent.do` | 按课程查看 |

---

## 1. 评价结果列表

```
GET /academic/eva/index/resultlist.jsdo
```

### 1.1 响应

| 项目 | 值 |
| --- | --- |
| 状态 | `200` |
| 编码 | **GBK** |
| 标题 | 教师教学评价 |
| 大小 | 约 4.9 KB |

### 1.2 响应结构

```html
<table cellpadding="0" cellspacing="0" class="infolist_tab">
  <tr>
    <th>教师</th>
    <th>课程(课程号)</th>
    <th>评估状态</th>
    <th>操作</th>
  </tr>
</table>
```

| 列 | 说明 |
| --- | --- |
| 教师 | 任课教师姓名 |
| 课程(课程号) | 课程名称与课程号 |
| 评估状态 | 是否已完成评价 |
| 操作 | 评价入口链接 |

返回的是 HTML 片段（`.jsdo` 的约定）。

### 1.3 空表的情况

若表格只有表头没有数据行，说明当前没有待评价或已评价的课程。
**这不是接口故障**，客户端应显示空状态而非报错。

判断方式：

```js
const rows = doc.querySelectorAll('table.infolist_tab tr');
// 第 1 行是表头，后续才是数据
const hasData = rows.length > 1;
```

### 1.4 评估状态

`评估状态` 列的值决定「操作」列是否可点击：

| 状态 | 含义 |
| --- | --- |
| 未评价 | 提供评价入口 |
| 已评价 | 可能只显示查看 |

评价提交本身是一个独立的表单接口，本模块未覆盖——
需要在评价开放期间从页面中提取。

---

## 2. 教学报告

```
GET /academic/teacher/teachingtask/schoolTeachingReportIndexStudent.do?scoreid={成绩记录 ID}
```

| 参数 | 必需 | 说明 |
| --- | --- | --- |
| `scoreid` | 是 | 成绩记录 ID，如 `233727590` |

来源是 `student/currcourse/currcourse.jsdo` 课程列表「教学记录」列中的链接，
**每门课一条**。

这是教师视角的教学报告在学生端的入口，通常包含课程的教学安排、
考核方式等信息。需要带真实 `scoreid` 请求，否则返回「提示信息」错误页。

> `scoreid` 是**成绩记录 ID**，不是课程号也不是课序号。
> 同一门课不同学期的 `scoreid` 不同。

---

## 3. 实现建议

### 3.1 与课程数据的关联

`scoreid` 只能从 `currcourse.jsdo` 获取，因此教学报告功能依赖该接口。
关联链路：

```
currcourse.jsdo
  └─ 课程列表「教学记录」列
       ├─ course_detail.jsdo?cid=          → 课程详情
       ├─ showTeacherInfoItem.do?userid=   → 教师信息
       └─ schoolTeachingReportIndexStudent.do?scoreid=  → 教学报告
```

建议解析 `currcourse.jsdo` 时**一次性把这三个 ID 都提取出来**并缓存，
避免后续为了拿某个 ID 而重复请求。

### 3.2 常见问题

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 表格无数据行 | 当前无评价记录 | 正常情况，显示空状态 |
| 教学报告返回错误页 | `scoreid` 为空或过期 | 从 `currcourse.jsdo` 重新提取 |
| 中文乱码 | 按 UTF-8 解码了 | 本模块是 GBK |
| 找不到评价入口 | 不在评价开放期 | 评价功能有时间窗口 |

### 3.3 展示建议

- 按学期分组展示评价记录；
- 未评价的课程优先展示并提示截止时间（若有）；
- 如果只做只读展示，不必实现评价提交——评价涉及主观内容，
  引导用户到教务系统原生界面完成更合适。
