# 09 · 通知公告与校历

本模块包含教学运行公告、校历、课程通知与消息已读标记。

其中**校历是课表功能的重要补充**——它提供真实的开学日期，
用于把「第 N 周」换算成具体日期（见 [`03-timetable.md` §5](./03-timetable.md)）。

---

## 接口清单

| 接口 | 方法 | 路径 | 说明 |
| --- | --- | --- | --- |
| 教学运行公告 | GET | `calendarinfo/viewCalendarInfo.do` | 按周查看公告 |
| 校历安排 | GET | `calendar/calendarViewList.do` | 全校校历 |
| 校历详情 | GET | `calendar/findSchoolCalendarDetailByDate.do` | 按日期查详情 |
| 课程通知 | GET | `manager/notify/notify_list_student.jsdo` | 课程相关通知 |
| 标记已读 | POST | `calendarinfo/updateUserMessageBrowseStatus.do` | **会修改状态** |

---

## 1. 教学运行公告

```
GET /academic/calendarinfo/viewCalendarInfo.do?week={周次}
```

| 参数 | 必需 | 说明 |
| --- | --- | --- |
| `week` | 否 | 周次，`1`～`26`。省略则显示当前周 |

### 1.1 响应

| 项目 | 值 |
| --- | --- |
| 状态 | `200` |
| 编码 | **UTF-8** |
| 标题 | 教学运行公告 |
| 大小 | 约 11 KB |

### 1.2 响应结构

页面由三个部分组成：

**① 周次选择器**——26 个周次标签，当前周带 `cur` class：

```html
<td class=" select"><a href="viewCalendarInfo.do?week=1"><span>01</span></a></td>
<td class=""><a href="viewCalendarInfo.do?week=2"><span>02</span></a></td>
…
<td class="cur"><a href="viewCalendarInfo.do?week=6"><span>06</span></a></td>
```

**② 当前周次信息**：

```html
<div class="curweek">
    2026 秋
    第<strong>6</strong>周
</div>
```

当前周次可以用 `cur` class 判定：

```js
const currentWeekEl = doc.querySelector('.week td.cur span');
const currentWeek = currentWeekEl ? Number(currentWeekEl.textContent) : null;
```

**③ 公告正文容器**：

```html
<div class="textwrap" id="textwrapper">
    <!-- 内容由 AJAX 动态填充，直接请求时为空 -->
</div>
```

> ⚠️ **`textwrapper` 在直接请求时是空的。** 内容由页面脚本异步加载，
> 这也是为什么这个接口虽然返回 200 且页面完整，却「没有公告内容」。
> 公告正文需要单独请求，或从页面脚本中找出实际的数据源。

### 1.3 与 checkPassword.do 的关系

`checkPassword.do` 返回的内容与本接口完全一致（同为约 11 KB 的教学运行公告）。
也就是说**主框架默认页就是教学运行公告**，两者可以互相替代。

---

## 2. 校历安排

```
GET /academic/calendar/calendarViewList.do
```

| 项目 | 值 |
| --- | --- |
| 状态 | `200` |
| 编码 | **UTF-8** |
| 标题 | 校历安排 |
| 大小 | 约 5.9 KB |

### 2.1 响应结构

页面标题区显示当前校历名称：

```html
<div class="title output_ctx">
  <span class="left"><em>2026秋（26秋校历） 校历</em></span>
```

校历名称的格式是 `{学年}{学期}（{简称}） 校历`，从中可以解析出学年学期。

### 2.2 导出 Word

```html
<input type="button" class="button" value="输出WORD"
       onclick="outputWord('2026秋（26秋校历）校历')">
```

`outputWord()` 接受校历名称作为参数，是导出入口。

### 2.3 制定校历

```html
<table class="button_tab">
  <td><input type="button" id="buttonAdd" class="button" value="制定校历"></td>
```

`制定校历` 是管理端功能，学生账号一般无权限。

### 2.4 与课表配合

校历提供的是**日期维度**的学期安排，可用于：

- 得到精确的开学日期（替代 [`03-timetable.md` §5.1](./03-timetable.md) 的估算方式）；
- 识别节假日、调休、补课安排；
- 在教学周与自然日期之间建立映射。

---

## 3. 校历详情

```
GET /academic/calendar/findSchoolCalendarDetailByDate.do?dateVal={日期}
```

| 参数 | 必需 | 说明 |
| --- | --- | --- |
| `dateVal` | 是 | 日期值 |

来源是 `calendar/calendarViewList.do` 页面中的链接：

```html
<a href="findSchoolCalendarDetailByDate.do?dateVal=...">…</a>
```

> ⚠️ **相对地址必须相对页面所在目录解析。** 页面是
> `calendar/calendarViewList.do`，所以真实路径是
> `calendar/findSchoolCalendarDetailByDate.do`，
> 而不是 `findSchoolCalendarDetailByDate.do`（会 404）。
>
> 详见 [`00-overview.md` §5.4](./00-overview.md)。

---

## 4. 课程通知

```
GET /academic/manager/notify/notify_list_student.jsdo
```

| 项目 | 值 |
| --- | --- |
| 状态 | `200` |
| 编码 | GBK |
| 标题 | 课程通知 |
| 大小 | 约 1.3 KB |

表头：

```
通知标题 | 课程号 | 课程名称 | 课序号 | 创建人 | 创建时间 | 修改时间
```

返回的是 HTML 片段（`.jsdo` 的约定）。若当前没有通知，表格只有表头没有数据行——
**这属于正常情况**，注意区分「无数据」和「接口失败」。

---

## 5. 标记消息已读

```
POST /academic/calendarinfo/updateUserMessageBrowseStatus.do?mid={消息 ID}
```

| 参数 | 必需 | 说明 |
| --- | --- | --- |
| `mid` | 是 | 消息 ID |

来源是门户页的内联脚本：

```js
$.ajax({
  type: "post",
  url: "<base>/calendarinfo/updateUserMessageBrowseStatus.do?mid=" + mid
});
```

| 项目 | 值 |
| --- | --- |
| 影响 | **会改变消息的已读状态** |

> ⚠️ **必须用 POST。** 用 GET 请求会返回「提示信息」错误页——
> 这类 AJAX 接口的请求方式由页面脚本决定，从地址本身看不出来。

---

## 6. 实现建议

### 6.1 公告内容的获取

由于 `viewCalendarInfo.do` 的正文容器是空的，展示公告需要额外处理：

| 方案 | 说明 |
| --- | --- |
| 抓取页面脚本 | 从 `viewCalendarInfo.do` 的 JS 中找出实际的数据请求地址 |
| 用 `week` 参数逐个请求 | 部分版本会在服务端渲染正文，可先用一个具体周次验证 |

建议先用探测工具请求 `calendarinfo/viewCalendarInfo.do?week=1` 和 `?week=6`，
对比响应差异来确认行为。

### 6.2 校历数据的利用

```
1. GET calendar/calendarViewList.do   → 解析校历名称，得到当前学年学期
2. 从校历中提取开学日期
3. 结合课表的「第 N 周」→ 换算成具体日期
```

这比 [`03-timetable.md` §5.1](./03-timetable.md) 的反推法更准确，
建议优先使用。

### 6.3 常见问题

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 公告正文为空 | 内容由 AJAX 加载 | 见 §6.1 |
| 校历详情 404 | 相对路径解析错误 | 相对页面目录解析 |
| 标记已读返回错误页 | 用了 GET | 改用 POST |
| 课程通知表格无数据行 | 当前无通知 | 正常情况 |
| 中文乱码 | 编码判断错误 | 公告/校历是 UTF-8，课程通知是 GBK |

### 6.4 缓存建议

公告与校历变动不频繁，建议：

- 公告按周缓存（`week` 作为缓存键）；
- 校历缓存整个学期，只在学期切换时更新；
- 不要每次进入首页都重新拉取公告。
