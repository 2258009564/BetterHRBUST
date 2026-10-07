# 07 · 教室查询

功能名为「教室时间占用」，用于查询某个教室在指定周次的排课情况，
是找空闲教室（自习、活动场地）的基础。

真正的入口在 **`teacher/teachresource/`** 下，而不是想当然的 `manager/classroom/`。

---

## 接口清单

| 接口 | 方法 | 路径 | 说明 |
| --- | --- | --- | --- |
| 查询表单 | GET | `teacher/teachresource/roomschedulequery.jsdo` | 教学楼 / 教室 / 周次选择 |
| 按教室查询 | POST | `teacher/teachresource/roomschedule.jsdo` | 指定教室的整周占用 |
| 按周查询 | POST | `teacher/teachresource/roomschedule_week.jsdo` | 指定周次的教室占用 |

---

## 1. 查询表单

```
GET /academic/teacher/teachresource/roomschedulequery.jsdo
```

### 1.1 响应

| 项目 | 值 |
| --- | --- |
| 状态 | `200` |
| 编码 | **GBK** |
| 大小 | 约 5.4 KB |
| 标题栏 | 教室时间占用 |

### 1.2 表单结构

```html
<form name="form1" method="post" action="roomschedulequery.jsdo">
```

表单 action 指向自身（相对路径），选择条件后重新提交本页以刷新下级选项。

### 1.3 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `aid` | select | 教学区 |
| `buildingid` | select | 教学楼 |
| `room` | select | 教室 |
| `whichweek` | select | 周次 |
| `week` | — | 周次（另一组提交使用） |

前两个下拉带自动提交，构成级联：

```html
<select name="aid" onChange="this.form.submit()">
<select name="buildingid" id="buildingid" onChange="this.form.submit()">
```

即**选择教学区后页面重新加载，才会出现对应的教学楼列表**。
解析时必须按这个顺序逐级获取。

### 1.4 教学区选项

```html
<option value="-1">请选择</option>
<option value="353">西区</option>
<option value="712">南区</option>
<option value="17491">荣成校区</option>
```

### 1.5 教学楼选项（西区示例）

```html
<option value="1168">国际</option>
<option value="354">金工实习</option>
<option value="3428">理工大厦</option>
<option value="4470">纳米楼</option>
<option value="3792">实训楼</option>
<option value="356">实验</option>
<option value="1248">体育馆</option>
<option value="2388">网络通识课</option>
<option value="4198">西区7号楼</option>
<option value="703">西区操场</option>
<option value="358">西区二号楼</option>
<option value="692">西区计算机楼</option>
<option value="449">西区三号楼</option>
<option value="481">西区四号楼</option>
<option value="579">西区图书馆</option>
<option value="488">西区五号楼</option>
<option value="584">西区一号楼</option>
<option value="1688">新教学楼</option>
```

> 教学楼 ID 与教学区绑定。`buildingid` 只有在选定 `aid` 之后才有效，
> 不要跨教学区复用。

### 1.6 两个提交按钮

页面有两个 `确定` 按钮，分别调用不同的 JS 函数：

```html
<input type="button" class="button" value="确定" onclick="checkForm()">
<input type="button" class="button" value="确定" onclick="checkWeekForm()">
```

| 函数 | 提交到 |
| --- | --- |
| `checkForm()` | `roomschedule.jsdo` —— 按教室查询 |
| `checkWeekForm()` | `roomschedule_week.jsdo` —— 按周查询 |

两者用途不同，见下文。

---

## 2. 按教室查询

```
POST /academic/teacher/teachresource/roomschedule.jsdo
```

| 参数 | 必需 | 说明 |
| --- | --- | --- |
| `buildingid` | 是 | 教学楼 ID |
| `room` | 是 | 教室 |
| `whichweek` | 否 | 周次 |

返回**指定教室在所有周次的占用情况**。适合「这个教室什么时候空」的场景。

---

## 3. 按周查询

```
POST /academic/teacher/teachresource/roomschedule_week.jsdo
```

| 参数 | 必需 | 说明 |
| --- | --- | --- |
| `whichweek` | 是 | 周次 |
| `week` | 否 | 周次（与 `whichweek` 配合） |

返回**指定周次内各教室的占用情况**。适合「这周哪间教室空」的场景。

---

## 4. 找空闲教室

服务端返回的是「占用」数据，空闲教室需要自行推算：

```
空闲 = 全部可用的 (星期, 节次) 组合 − 已占用的组合
```

实现步骤：

```
1. 调 roomschedule_week.jsdo 取指定周次的占用表
2. 建立占用集合：Set<`${weekday}-${section}`>
3. 遍历 7 天 × 12 节，排除占用组合
4. 按「连续节次」合并成可用时段（如第 3-4 节连堂）
```

合并连续节次的示例：

```js
function findFreeSlots(occupied, { days = 7, sections = 12, minSpan = 2 } = {}) {
  const slots = [];

  for (let day = 1; day <= days; day += 1) {
    let start = null;

    for (let sec = 1; sec <= sections + 1; sec += 1) {
      const isFree = sec <= sections && !occupied.has(`${day}-${sec}`);

      if (isFree && start === null) {
        start = sec;
      } else if (!isFree && start !== null) {
        const span = sec - start;
        if (span >= minSpan) slots.push({ day, start, end: sec - 1, span });
        start = null;
      }
    }
  }
  return slots;
}
```

`minSpan` 用于过滤掉零散的单节空档，默认要求至少连续 2 节。

---

## 5. 实现建议

### 5.1 请求顺序

```
1. GET  roomschedulequery.jsdo                     → 拿到教学区列表
2. GET  roomschedulequery.jsdo?aid=353             → 拿到西区的教学楼列表
3. GET  roomschedulequery.jsdo?aid=353&buildingid=584  → 拿到该楼的教室列表
4. POST roomschedule_week.jsdo?whichweek=6&week=6  → 拿到该周的占用数据
```

前 3 步是级联的，**不能跳步**——直接请求第 3 步会因为缺少上级选择
而拿不到教室列表。

> 这条链路较长。如果只是做「查找空闲教室」，建议把第 1～3 步的结果
> 缓存起来（教学楼和教室列表很少变动），只在查询占用数据时实时请求。

### 5.2 常见问题

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 404 | 用了 `manager/classroom/*` 路径 | 真实路径在 `teacher/teachresource/` 下 |
| 教学楼下拉为空 | 没先选教学区 | 按级联顺序请求 |
| 教室下拉为空 | 没先选教学楼 | 同上 |
| 返回「提示信息」错误页 | 缺少 `buildingid` 等必需参数 | 补全参数后重试 |
| 中文乱码 | 按 UTF-8 解码了 | 本模块是 GBK |
