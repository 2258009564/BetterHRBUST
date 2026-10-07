# 04 · 个人信息与学籍

本模块包含学生上下文（其他模块依赖的基础数据）、个人资料、学籍信息、
学生照片，以及学籍相关的辅助接口。

> ⚠️ **本模块返回身份证号、手机号、家庭住址等敏感信息。**
> 前端展示需脱敏，不要写入日志或上传到第三方。

---

## 接口清单

| 接口 | 方法 | 路径 | 说明 |
| --- | --- | --- | --- |
| 学生上下文 | GET | `student/currcourse/currcourse.jsdo` | **内部 ID、学年、学期、本学期课程** |
| 我的信息 | GET | `showPersonalInfo.do` | 个人资料 + 学籍异动 |
| 学籍信息 | GET | `student/studentinfo/studentInfoModifyIndex.do` | 完整学籍表单 |
| 学生照片 | GET | `manager/studentinfo/showStudentImage.jsp` | 学籍页所用 |
| 学生照片 | GET | `student/studentinfo/loadphoto_added.jsdo` | 我的信息页所用 |
| 级联下拉 | GET | `student/studentinfo/change*.do` | 院系→专业→班级→校区 |
| 汉字转拼音 | GET | `student/studentinfo/studentHanZI2PinYin.do` | 自动生成拼音 |
| 保存学籍 | POST | `student/studentinfo/studentInfoModifyDo.do` | **会修改数据** |
| 重修重考报名 | GET | `student/remajor/signupsetting.jsdo` | |
| 等级考试管理 | GET | `student/skilltest/skilltest.jsdo` | |
| 等级考试成绩 | GET | `student/queryscore/skilltestscore.jsdo` | |

---

## 1. 学生上下文

```
GET /academic/student/currcourse/currcourse.jsdo
```

**这是登录后第一个应该调用的接口。** 它同时完成两件事：
校验会话是否有效，以及提供其他模块依赖的上下文参数。

### 1.1 响应

| 项目 | 值 |
| --- | --- |
| 状态 | `200` |
| 编码 | **GBK** |
| 标题 | 本学期课程安排 |
| 大小 | 约 32 KB |

### 1.2 提取上下文参数

响应中含有一个自定义标签，携带三个关键值：

```html
<eduaffair:CTRT studentid="100001" year="46" windowStyle="main"></eduaffair:CTRT>
```

```js
const match = html.match(/studentid=["']?(\d+)["']?[\s\S]*?year=["']?(\d+)["']?/);
// match[1] → studentid（教务内部 ID）
// match[2] → year（学年序号）
```

`term` 需要从下拉框的选中项获取：

```html
<select name="term">
  <option value="2" selected>秋</option>
  <option value="1">春</option>
</select>
```

```js
const termMatch = html.match(/<select[^>]*name=["']term["'][^>]*>([\s\S]*?)<\/select>/i);
const selected = termMatch[1].match(/<option[^>]*value=["'](\d+)["'][^>]*selected/i);
```

> ⚠️ **`studentid` 不是学号。** 例如内部 ID `100001` 对应学号 `2023000001`，两者完全不同。
> 课表等接口必须使用内部 ID。详见 [`00-overview.md` §4](./00-overview.md)。

### 1.3 本学期课程表

响应第一张表（class `infolist_tab`，数据行 class `infolist_common`）列出本学期课程：

```
课程号 | 课程序号 | 课程名称 | 任课教师 | 学分 | 选课属性 |
考核方式 | 考试性质 | 是否缓考 | 上课时间、地点 | 教材 | 教学记录
```

这张表是**唯一能同时拿到「学分 + 课程号 + 课序号」的地方**，
做课表增强或成绩分析时需要它。

「教学记录」列中可能含有指向以下接口的链接：

| 链接目标 | 说明 |
| --- | --- |
| `manager/querycourse/course_detail.jsdo?cid=` | 课程详情 |
| `manager/teacherinfo/showTeacherInfoItem.do?userid=` | 教师信息 |
| `teacher/teachingtask/schoolTeachingReportIndexStudent.do?scoreid=` | 教学报告 |

### 1.4 课节分组表

响应第二张表列出课节分组：

```
序号 | 名称 | 包含小节
```

### 1.5 学期切换

响应中含有一个 GET 表单，字段为 `year` 与 `term`，切换后重新请求本接口
即可查看其他学期的课程。

### 1.6 会话有效性判断

本接口是判断登录状态的最佳选择：

| 结果 | 含义 |
| --- | --- |
| 200 且含 `studentid` | 会话有效，同时拿到上下文 |
| 返回登录页 HTML | 会话失效，需重新登录 |

比用 `index.jsp` 可靠——后者必然 302 回登录页，无法反映真实会话状态。

---

## 2. 我的信息

```
GET /academic/showPersonalInfo.do
```

| 项目 | 值 |
| --- | --- |
| 状态 | `200` |
| 编码 | UTF-8 |
| 标题 | 我的信息 |
| 大小 | 约 4.4 KB |
| 表格 | 2 张 |

这是**最简洁的个人信息接口**，数据量小、结构规整，适合作为个人资料页的数据源。

### 2.1 基本信息表

表格 class 为 `form`，结构是 `<th>标签</th><td>值</td>` 的键值对：

| 字段 | 说明 |
| --- | --- |
| 用户名 | |
| 真实姓名 | |
| 所在院系 | |
| 专业 | |
| 方向 | |
| 学生类别 | 含学制，如「本科四年」 |
| 年级 | |
| 班级 | |
| 证件类型 | |
| 证件号码 | 🔒 敏感 |
| 电子邮箱 | 🔒 |
| 联系电话 | 🔒 |
| 通讯地址 | 🔒 |
| 邮政编码 | 🔒 |

解析这类键值表：

```js
const rows = doc.querySelectorAll('table.form tr');
const info = {};
rows.forEach((row) => {
  const th = row.querySelector('th');
  const td = row.querySelector('td');
  if (th && td) {
    info[th.textContent.trim()] = td.textContent.trim();
  }
});
```

### 2.2 学籍异动表

第二张表 class 为 `datalist`（数据行 `infolist_hr_common`）：

```
异动类型 | 异动时间 | 原因 | 备注
```

---

## 3. 学籍信息

```
GET /academic/student/studentinfo/studentInfoModifyIndex.do
```

| 项目 | 值 |
| --- | --- |
| 状态 | `200` |
| 编码 | UTF-8 |
| 标题 | 学籍信息 |
| 大小 | 约 31 KB |
| 表格 | 6 张 |
| 表单 | POST → `studentInfoModifyDo.do` |

这是**最完整的学籍数据来源**，比「我的信息」多了入学、毕业等信息。

### 3.1 包含的信息表

| 表 | 内容 |
| --- | --- |
| 基本信息 | 学号、姓名、性别、出生日期、民族、政治面貌 |
| 院系专业班级 | 院系、专业、年级、学生类别、班级、校区 |
| 联系方式 | 邮箱、手机、QQ、微信、地址 |
| 入学信息 | 入学日期、考生号、高考总分、毕业中学 |
| 毕业信息 | 毕业类型、学位 |
| 异动记录 | 异动类型、异动日期、异动原因、异动前后班级 |

### 3.2 解析注意

**① 页面含约 100 个 hidden 字段。**

这些字段是表单提交所需，解析时不要把它们当成有效数据。

**② 不要用 `class="error"` 判定页面异常。**

页面中大量使用 `<em class="error"></em>` 作为必填校验提示的占位元素，
按此判定会把本页误判为错误页。

**③ 必须剥离 HTML 注释。**

页面里有被注释掉的历史表单字段，不剥离会导致表头与列错位。

---

## 4. 学生照片

有两个接口，分别服务于不同页面。

### 4.1 学籍页所用

```
GET /academic/manager/studentinfo/showStudentImage.jsp?id={随机小数}&dataName=photo
```

| 参数 | 说明 |
| --- | --- |
| `id` | 随机小数值，如 `0.8273641` |
| `dataName` | 固定 `photo` |

页面中的原始写法：

```html
<img src="/academic/manager/studentinfo/showStudentImage.jsp?id=0.82736&dataName=photo"
     width="120" hight="150">
```

响应为二进制图片，直接作为 `<img>` 的 `src` 使用即可（需带 Cookie）。

### 4.2 我的信息页所用

```
GET /academic/student/studentinfo/loadphoto_added.jsdo
      ?primary=userid&kind=student&userid={加密串}
```

| 参数 | 说明 |
| --- | --- |
| `primary` | 固定 `userid` |
| `kind` | 固定 `student` |
| `userid` | **加密串**，不是明文 ID |

页面中的原始写法：

```html
<img src="/academic/student/studentinfo/loadphoto_added.jsdo?primary=userid&kind=student&userid=AbCdEfGhIjKlMnOpQrStUv=="
     height="108">
```

> ⚠️ `userid` 是加密串，**必须从页面响应中提取**，不能自己拼接。
> 详见 [`00-overview.md` §4.6](./00-overview.md)。

---

## 5. 级联下拉接口

学籍信息页的院系→专业→班级→校区选择是级联下拉，通过一组 `.do` 接口驱动：

| 接口 | 参数 | 说明 |
| --- | --- | --- |
| `student/studentinfo/changeMajor.do` | `departmentid` | 按院系取专业 |
| `student/studentinfo/changeDirection.do` | `majorid` | 按专业取方向 |
| `student/studentinfo/changeClass.do` | `majorid` | 按专业取班级 |
| `student/studentinfo/changeOnylClass.do` | `majorid` | 按专业取班级（另一入口） |
| `student/studentinfo/changeArea.do` | `classid` | 按班级取校区 |
| `student/studentinfo/changeLeanStyle.do` | `classid` | 按班级取学习形式 |

**这些接口必须带真实 ID 调用。** 直接请求（参数为空）会返回「提示信息」错误页。

响应为 HTML `<option>` 片段：

```html
<option value="1234">计算机科学与技术</option>
```

一般不需要主动调用——只有做「学籍信息编辑」功能时才用得上。
只读展示时直接用学籍信息页已渲染好的选项即可。

---

## 6. 汉字转拼音

```
GET /academic/student/studentinfo/studentHanZI2PinYin.do?{参数}
```

用于在填写拼音姓名时自动生成。无参数请求返回 200 但内容为空。
仅在实现学籍编辑功能时才有用。

---

## 7. 保存学籍信息

```
POST /academic/student/studentinfo/studentInfoModifyDo.do
```

| 项目 | 值 |
| --- | --- |
| 表单字段 | 约 100 个，含学号、姓名、院系、专业、班级、联系方式、入学/毕业信息等 |
| 影响 | **会修改学籍数据** |

> ⚠️ **这个接口会真实修改学籍信息。** 实现修改功能时必须：
> - 从 `studentInfoModifyIndex.do` 完整取出所有字段再提交，不要只提交改动项；
> - 提交前让用户二次确认；
> - 做好失败回滚与错误提示。
>
> 探测工具默认不请求此类接口。

---

## 8. 重修重考报名

```
GET /academic/student/remajor/signupsetting.jsdo
```

| 项目 | 值 |
| --- | --- |
| 状态 | `200` |
| 标题 | 重修重考报名 |
| 大小 | 约 6.4 KB |

响应含两张表：

| 表 | 表头 |
| --- | --- |
| 报名设置 | 类别、报名时间、报考学期 |
| 可报课程 | 考试性质、课程号、课程名称、选课属性、学分… |

---

## 9. 等级考试

### 9.1 等级考试管理

```
GET /academic/student/skilltest/skilltest.jsdo
```

| 项目 | 值 |
| --- | --- |
| 状态 | `200` |
| 标题 | 等级考试管理 |
| 大小 | 约 3.7 KB |

两张表：

| 表 | 表头 |
| --- | --- |
| 可报名考试 | 考试名称、考试时间、报名条件、说明、报名 |
| 已报名 | 考试名称、考试时间、准考证号… |

### 9.2 等级考试成绩

```
GET /academic/student/queryscore/skilltestscore.jsdo
```

| 项目 | 值 |
| --- | --- |
| 状态 | `200` |
| 标题 | 等级考试成绩查询 |
| 大小 | 约 1.1 KB |

表头：`考试名称 | 考试时间 | 成绩`

---

## 10. 实现建议

### 10.1 数据获取顺序

```
1. currcourse.jsdo          → 会话校验 + studentid/year/term + 本学期课程
2. showPersonalInfo.do      → 个人资料（界面首屏够用）
3. studentInfoModifyIndex.do → 完整学籍（进入详情页时再拉）
```

第 3 步数据量大且含敏感字段，建议**按需加载**，不要放在首屏。

### 10.2 敏感字段处理

```js
/** 证件号脱敏 */
function maskIdCard(id) {
  if (!id || id.length < 8) return id;
  return id.slice(0, 4) + '*'.repeat(id.length - 8) + id.slice(-4);
}

/** 手机号脱敏 */
function maskPhone(phone) {
  if (!phone || phone.length < 7) return phone;
  return phone.slice(0, 3) + '****' + phone.slice(-4);
}
```

默认脱敏显示，提供「显示完整信息」按钮由用户主动触发。
同时确保这些值不会被写入 `console.log`、错误上报或分析埋点。

### 10.3 常见问题

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| `currcourse.jsdo` 返回登录页 | 会话失效 | 重新登录 |
| `studentid` 提取为空 | 页面结构变化或未登录 | 检查是否返回登录页 |
| 课表接口返回空 | 把学号当成了 `studentid` | 改用内部 ID |
| 学籍页被判定为「错误页」 | 用了 `class="error"` 判定 | 改用「提示信息」标题判定 |
| 照片请求 403/登录页 | 缺少 Cookie 或 `userid` 拼接错误 | 带上 Cookie，加密串从页面取 |
