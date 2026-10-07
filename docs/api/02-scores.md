# 02 · 成绩查询

成绩数据由**同一个接口**提供，通过参数区分「全部成绩」与「按学期成绩」。
本模块还包含学年学期下拉框的数据源。

---

## 接口清单

| 接口 | 方法 | 路径 | 说明 |
| --- | --- | --- | --- |
| 成绩查询 | POST | `manager/score/studentOwnScore.do` | 主接口，支持全部/按学期/按属性筛选 |
| 成绩查询 | GET | `manager/score/studentOwnScore.do` | 与 POST 等价 |
| 学年学期列表 | GET | `calendar/showCalendarYearTerm.do` | 学期下拉框的数据源 |

---

## 1. 成绩查询

```
POST /academic/manager/score/studentOwnScore.do
Content-Type: application/x-www-form-urlencoded
```

GET 与 POST **完全等价**，返回内容一致。参数放 query 或 body 都可以。

### 1.1 需要登录

是。未登录会返回登录页 HTML（HTTP 200）。

### 1.2 请求参数

| 参数 | 必需 | 取值 | 说明 |
| --- | --- | --- | --- |
| `year` | 否 | 空 = 全部；否则学年序号 | 如 `46` 表示 2026 |
| `term` | 否 | 空 = 全部；`1` = 春，`2` = 秋 | |
| `prop` | 否 | 空 = 全部，`0` = 必修，`1` = 限选，`2` = 任选 | 选课属性筛选 |
| `groupName` | 否 | — | 课组筛选 |
| `para` | 否 | 默认 `0` | 隐藏字段 |
| `sortColumn` | 否 | 字段名 | 排序列，见 §1.6 |
| `failedStatus` | 否 | `1` | 勾选则只显示未通过课程 |
| `passedStatus` | 否 | `1` | 勾选则只显示及格课程 |
| `maxStatus` | 否 | `1` | 勾选则只显示最高分 |

**取全部成绩**（最常用）：

```
year=&term=&prop=&groupName=&para=0&sortColumn=
```

**取指定学期**：

```
year=46&term=2&prop=&groupName=&para=0&sortColumn=
```

> 三个 `*Status` 参数是复选框，未勾选时**不要发送**。
> 发送 `failedStatus=0` 与不发送该参数的行为可能不同。

### 1.3 响应

| 项目 | 值 |
| --- | --- |
| 状态 | `200` |
| 类型 | `text/html;charset=UTF-8` |
| **编码** | **UTF-8** |
| 标题 | 个人成绩查询 |
| 大小 | 全部成绩约 55 KB |

**这个接口是 UTF-8，不是 GBK。** 按 GBK 解码会导致中文乱码，
且关键字匹配全部失败。详见 [`00-overview.md` §3](./00-overview.md)。

### 1.4 响应结构

页面是一张「查询条件表」加一张「成绩数据表」。

**数据表**：表格 class 为 `datalist`（不是 `infolist_tab`）。

真实表头（共 14 列）：

```
学年 | 学期 | 课程号 | 课程名 | 课序号 | 课组 | 总评 | 学分 | 学时 |
选课属性 | 备注 | 考试性质 | 及格标志 | 过程性成绩
```

### 1.5 解析要点

**① 总评列叫「总评」，不叫「成绩」。**

这是最容易踩的坑：列名是 `总评`，而后面还有一个 `过程性成绩` 列**含有「成绩」二字**。

```js
// 错误：会先匹配到「过程性成绩」列
const col = headers.findIndex((h) => h.includes('成绩'));

// 正确：精确匹配
const col = headers.findIndex((h) => h.trim() === '总评');

// 或者按优先级显式指定
const COL_TOTAL = headers.findIndex((h) => /^总评$/.test(h.trim()));
```

**② 不及格课程的「总评」可能是文字。**

`及格标志` 列为 `不及格` 时，总评列可能出现非数字内容。
解析数值前要先判断，不要直接 `parseFloat`。

**③ 表头里可能含空白与换行。**

真实 HTML 中表头是这样写的，取 `textContent` 后必须 `trim()`：

```html
<th>

    总评

</th>
```

### 1.6 排序

页面提供了 `sort()` 函数用于按列排序：

```js
function sort(sortColumnValue) {
  document.form1.sortColumn.value = sortColumnValue;
  document.form1.submit();
}
```

即把列名赋给 `sortColumn` 后重新提交。这是一个隐藏字段，
常规查询请求中传空即可。

### 1.7 学年序号

`year` 使用累加序号，与公元年份的关系是：

```
year = 公元年份 − 1980
```

响应中的下拉框列出了全部可选值：

```html
<select name="year">
  <option value="">全部</option>
  <option value="46">2026</option>
  <option value="26">2006</option>
  <option value="27">2007</option>
  ...
  <option value="66">2046</option>
</select>
```

| 序号 | 年份 | 序号 | 年份 |
| ---: | --- | ---: | --- |
| 26 | 2006 | 46 | 2026 |
| 27 | 2007 | 47 | 2027 |
| 28 | 2008 | 48 | 2028 |
| … | … | … | … |

> 不要自己拼接年份值。虽然存在固定偏移，但可选范围由服务端下发，
> 应以响应中的下拉框为准。

### 1.8 学期取值

```html
<select name="term">
  <option value="">全部</option>
  <option value="2">秋</option>
  <option value="1">春</option>
</select>
```

注意**秋是 `2`、春是 `1`**，不是常见的 `1`/`2` 顺序。

---

## 2. 学年学期列表

```
GET /academic/calendar/showCalendarYearTerm.do?type=alias&yearId=
```

| 参数 | 必需 | 说明 |
| --- | --- | --- |
| `type` | 是 | 固定为 `alias` |
| `yearId` | 否 | 学年序号，留空返回全部 |

用于构建学期选择器。若不需要动态列表，直接用 `studentOwnScore.do`
响应里已有的下拉框数据也可以。

---

## 3. 计算 GPA

教务系统**不提供 GPA**，需要自行计算。常用算法（4.0 制，五分制换算）：

```js
/**
 * 单门课程绩点
 * 总评 >= 60 时：(总评 − 50) / 10，上限 4.0
 * 总评 <  60 时：0
 */
function calculateGPA(score) {
  const s = Number(score);
  if (!Number.isFinite(s) || s < 60) return 0;
  return Math.min(4.0, (s - 50) / 10);
}
```

**等级制成绩换算**：

| 等级 | 折算分 |
| --- | ---: |
| 优秀 / 优 | 95 |
| 良好 / 良 | 85 |
| 中等 / 中 | 75 |
| 及格 | 65 |
| 不及格 | 0 |

**平均学分绩点**：

```
GPA = Σ(单科绩点 × 学分) / Σ学分
```

计算时应排除以下课程，否则结果会失真：

- 总评非数值的记录（如缓考、免修）；
- 学分缺失或为 0 的记录；
- 及格标志为「不及格」的重修记录（若学校按最高分计算，需先去重）。

> 各校、各学院的计算规则可能有差异，建议把算法做成可配置项，
> 并在界面上注明采用的规则。

---

## 4. 实现建议

### 4.1 数据获取策略

成绩数据量不大（一次约 55 KB），且变动不频繁。建议：

1. 首次进入时拉取全部成绩并本地缓存；
2. 提供「刷新」入口手动更新，不要每次进页面都全量请求；
3. 按学期筛选在**本地做**即可，不必为每次筛选都请求服务端。

### 4.2 错误处理

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 中文乱码 | 按 GBK 解码了 | 改用响应头 charset |
| 返回登录页 | 会话失效 | 提示重新登录 |
| 表格为空 | 该学期无成绩 | 正常情况，显示空状态 |
| 总评解析为 `NaN` | 遇到等级制或非数字总评 | 用等级换算表兜底 |

### 4.3 展示建议

- 按学期分组展示，默认展开最近学期；
- 显式标注「及格标志」，未通过的课程要突出；
- 若提供 GPA 计算，同时展示计算规则，避免与教务口径不一致引起困惑。
