# 06 · 课程与教师信息

本模块提供全校课程检索（支持按院系、学期、课程性质等多维筛选）以及教师详情。

课程查询是一个典型的「表单页 + 数据接口」结构：`index.jsdo` 渲染查询表单，
`course_list.jsdo` 返回结果。

---

## 接口清单

| 接口 | 方法 | 路径 | 说明 |
| --- | --- | --- | --- |
| 课程查询表单 | GET | `manager/querycourse/index.jsdo` | 查询条件页 |
| 课程查询结果 | POST | `manager/querycourse/course_list.jsdo` | 结果列表 |
| 课程详情 | GET | `manager/querycourse/course_detail.jsdo` | 按 `cid` 查详情 |
| 结果导出 | POST | `manager/querycourse/excel_exp.jsdo` | 导出 Excel |
| 教师信息 | GET | `manager/teacherinfo/showTeacherInfoItem.do` | 按 `userid` 查教师 |

---

## 1. 课程查询表单

```
GET /academic/manager/querycourse/index.jsdo
```

### 1.1 响应

| 项目 | 值 |
| --- | --- |
| 状态 | `200` |
| 编码 | **GBK** |
| 标题 | 课程查询 |
| 大小 | 约 34 KB |

页面大小主要来自「院系」下拉框——它列出了全校的院系、教研室层级结构。

### 1.2 表单结构

```html
<form name="form1" method="post" action="index.jsdo">
  …
</form>
```

注意 `action` 是 `index.jsdo`（自身），但页面 JS 会**动态改写 action**
再提交：

```js
fp.action = "course_list.jsdo";   // 点击「查询」
fp.action = "excel_exp.jsdo";     // 点击「导出」
```

这解释了为什么从页面里提取「表单 action」得到的地址不是真正的数据接口——
**必须同时分析表单的 action 和按钮触发的 JS**。

### 1.3 查询条件字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `depid` | select | 院系/部门。`1` = 哈尔滨理工大学（根节点），带层级缩进 |
| `depname` | hidden | 院系名称回显 |
| `terms` | 多选 | 学期 |
| `ctype` | 多选 | 课程性质 |
| `stusorts` | 多选 ×8 | 学生类别 |
| `trgroup` | select | 教师组 |
| `trgroupname` | hidden | 回显 |
| `keyword` | select | 课程关键字类型 |
| `keywordname` | hidden | 回显 |
| `keyvalue` | text | **关键字输入框** |
| `roomsort` | select | 教室类型 |
| `roomsortname` | hidden | 回显 |
| `emanner` | select | 考核方式 |
| `emannername` | hidden | 回显 |
| `emode` | select | 考试方式 |
| `emodename` | hidden | 回显 |
| `status` | select | 课程状态 |
| `statusname` | hidden | 回显 |
| `orderby` | — | 排序字段 |
| `orderseq` | — | 排序方向 |

> `*name` 结尾的都是 hidden 回显字段（把下拉框选中的**文本**带给服务端）。
> 提交时保持原值即可。

### 1.4 院系下拉的层级结构

`depid` 的选项用前缀短横线表示层级：

```html
<option value="1" selected>哈尔滨理工大学</option>
<option value="806">--学籍变动</option>
<option value="807">--教务处</option>
<option value="835">----教务处处领导</option>
<option value="808">--机械动力工程学院</option>
<option value="844">----机械工程系</option>
```

下拉框带自动提交：

```html
<select name="depid" onChange="this.form.submit();">
```

**这是一个级联表单**：选择院系后立即重新请求本页，服务端据此刷新
下级选项。实现时注意这一点，不要期望一次性拿到所有层级。

---

## 2. 课程查询结果

```
POST /academic/manager/querycourse/course_list.jsdo
Content-Type: application/x-www-form-urlencoded
```

**必须用 POST**，并携带完整的查询条件表单字段。用 GET 请求会返回错误页。

### 2.1 请求参数

沿用 §1.3 的字段。最小可用请求：

```
keyvalue=<关键字>&depid=1&terms=&ctype=&stusorts=&trgroup=&keyword=&roomsort=&emanner=&emode=&status=&orderby=&orderseq=
```

### 2.2 响应

| 项目 | 值 |
| --- | --- |
| 状态 | `200` |
| 类型 | HTML 片段 |
| 说明 | 结果表格 |

**与 `.jsdo` 的约定一致：返回 HTML 片段而非 JSON。**

### 2.3 解析

与其他列表页一致，按表头文字动态映射列。由于查询结果列较多，
推荐用 [通用表格解析](./00-overview.md#62-表格结构规律) 的方式处理。

---

## 3. 课程详情

```
GET /academic/manager/querycourse/course_detail.jsdo?cid={课程实例 ID}
```

| 参数 | 必需 | 说明 |
| --- | --- | --- |
| `cid` | 是 | 课程实例 ID，如 `248686` |

| 项目 | 值 |
| --- | --- |
| 状态 | `200` |
| 大小 | 约 4.4 KB |

`cid` 的来源是 `student/currcourse/currcourse.jsdo` 的课程列表中
「教学记录」列的链接。

> **`cid` 是课程实例 ID，不是课程号。** 同一门课在不同学期、不同教学班
> 会有不同的 `cid`。

**必须带真实 `cid`**：带参数时返回课程详情页；
不带参数则返回「提示信息」错误页。

---

## 4. 结果导出

```
POST /academic/manager/querycourse/excel_exp.jsdo
```

页面 JS 中定义为导出入口（`fp.action = "excel_exp.jsdo"`）。

**必须用 POST 携带完整的查询条件。** 用 GET 直接请求会返回 404——
这是从页面提取地址时容易误判的地方：看到 `.jsdo` 就直接 GET，
得到的 404 会让人以为路径写错了，实际是请求方式不对。

响应为 Excel 二进制文件。

---

## 5. 教师信息

```
GET /academic/manager/teacherinfo/showTeacherInfoItem.do?userid={教师 ID}
```

| 参数 | 必需 | 说明 |
| --- | --- | --- |
| `userid` | 是 | 教师 ID，如 `113679` |

| 项目 | 值 |
| --- | --- |
| 状态 | `200` |
| 大小 | 约 3.1 KB |

来源是 `student/currcourse/currcourse.jsdo` 课程列表中的教师姓名链接，
**每位任课教师一条**。

带真实 `userid` 时返回教师信息表：

```
姓名 | 性别 | 院系 | 教研组
```

**必须带真实 `userid`**：不带参数则返回「提示信息」错误页。

> 注意 `userid` 是**教师**的明文 ID，与学生的内部 ID（见
> [`00-overview.md` §4](./00-overview.md)）不是同一套编号，不要混用。

---

## 6. 实现建议

### 6.1 课程信息的三个来源

课程数据分散在几处，用途不同：

| 来源 | 提供的数据 | 适用场景 |
| --- | --- | --- |
| `student/currcourse/currcourse.jsdo` | **本人本学期课程**：课程号、课序号、学分、教师、教材 | 课表增强、成绩关联 |
| `manager/querycourse/index.jsdo` | **全校课程检索**：按院系/学期/性质筛选 | 课程查询功能 |
| `manager/querycourse/course_detail.jsdo` | **单门课详情** | 课程详情页 |

如果只是给课表补充「学分、课程号」，用第一个接口最省事——
一次请求就能拿到本学期全部课程，不必逐个查 `cid`。

### 6.2 关联课表数据

课表单元格里只有课程名和课序号（见 [`03-timetable.md` §1.5](./03-timetable.md)），
缺课程号和学分。关联方式：

```js
// 用「课程名 + 课序号」作为关联键
const key = (courseName, courseSeq) => `${courseName}#${courseSeq}`;

const courseMap = new Map(
  currentCourses.map((c) => [key(c.courseName, c.courseSeq), c])
);

const enriched = timetableCell => {
  const info = courseMap.get(key(timetableCell.courseName, timetableCell.courseSeq));
  return { ...timetableCell, courseId: info?.courseId, credit: info?.credit };
};
```

### 6.3 常见问题

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| `course_list.jsdo` 返回错误页 | 用了 GET | 改用 POST 并带完整条件 |
| `excel_exp.jsdo` 返回 404 | 用了 GET | 改用 POST |
| `course_detail.jsdo` 返回错误页 | `cid` 为空或过期 | 从 `currcourse.jsdo` 重新提取 |
| 查询表单提交后页面没变 | 忽略了下拉框的 `onChange` 自动提交 | 该表单是级联的，选择即提交 |
| 中文乱码 | 按 UTF-8 解码了 | 本模块是 GBK |

### 6.4 使用限制

课程查询面向全校数据，结果可能很大。建议：

- 强制要求至少一个查询条件，不允许无条件全量查询；
- 结果分页展示，不要一次渲染上千条；
- 对常用查询做本地缓存。
