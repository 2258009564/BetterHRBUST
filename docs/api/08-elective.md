# 08 · 选课

选课功能在 **`manager/electcourse/`** 下，入口是一个跳转页。
需要说明的是：**选课数据只在选课开放期间可用**，非选课期访问会返回错误页。

---

## 接口清单

| 接口 | 方法 | 路径 | 说明 |
| --- | --- | --- | --- |
| 选课入口 | GET | `student/selectcoursedb/jumppage.jsp` | 跳转页 |
| 选课主页 | GET | `manager/electcourse/elective.do` | 选课功能主界面 |
| 课节分组 | GET | `manager/electcourse/studentSelectCrgroupList.do` | 按学期选择课节分组 |

---

## 1. 选课入口

```
GET /academic/student/selectcoursedb/jumppage.jsp
```

| 项目 | 值 |
| --- | --- |
| 状态 | `200` |
| 编码 | GBK |
| 大小 | 约 1.3 KB |

页面中只有一个表单，用于跳转到选课主页：

```html
<form action="/academic/manager/electcourse/elective.do" method="get">
</form>
```

无字段，直接提交即可。

---

## 2. 选课主页

```
GET /academic/manager/electcourse/elective.do
```

| 项目 | 值 |
| --- | --- |
| 状态 | `200` |
| 说明 | 选课功能主界面 |

**这是选课功能的真实入口**（此前按命名规律推测的
`manager/elective/courseList.do` 是 404）。

### 2.1 关于「错误提示」页

非选课期访问该接口，会返回标题为「错误提示」的页面，
内容通常为「不在选课时间内」之类的提示。

**这属于正常行为，不代表接口不可用。** 客户端应当识别这种情况，
给出「当前不在选课时间」的友好提示，而不是报「接口错误」。

判定方式：

```js
const title = doc.querySelector('title')?.textContent.trim();
if (title === '错误提示' || title === '提示信息') {
  // 提取提示正文展示给用户
  const message = doc.body.textContent.trim();
  return { available: false, message };
}
```

### 2.2 功能范围

选课主页是完整的选课界面，包含：

- 可选课程列表（含容量、已选人数）
- 选课 / 退课操作
- 已选课程查看

> ⚠️ **选课与退课会真实修改教务数据。** 实现相关功能时：
> - 提交前必须让用户确认；
> - 服务端返回结果要如实展示（选课失败的原因很关键）；
> - 不要做自动化抢课——高频请求可能触发教务系统的限制。
>
> 探测工具默认不请求此类接口。

---

## 3. 课节分组

```
GET /academic/manager/electcourse/studentSelectCrgroupList.do?yearId={year}&termId={term}
```

| 参数 | 必需 | 说明 |
| --- | --- | --- |
| `yearId` | 是 | 学年序号 |
| `termId` | 是 | 学期序号（`1` 春 / `2` 秋） |

| 项目 | 值 |
| --- | --- |
| 状态 | `200` |
| 编码 | UTF-8 |
| 标题 | 选择课节分组 |
| 大小 | 约 3.7 KB |

来源是 `student/currcourse/currcourse.jsdo` 页面中的「选择课节分组」按钮：

```
studentSelectCrgroupList.do?yearId=46&termId=2
```

用于查看某学期的课节分组设置。

> 该接口在非选课期也可能返回空内容或提示页。

---

## 4. 实现建议

### 4.1 功能可用性判断

选课是有时间窗口的功能。建议在界面上做状态区分，而不是直接隐藏入口：

| 状态 | 判定 | 界面表现 |
| --- | --- | --- |
| 可访问 | 返回正常选课界面 | 正常展示 |
| 未开放 | 返回「错误提示」页 | 显示服务端提示文案，禁用操作按钮 |
| 会话失效 | 返回登录页 | 提示重新登录 |

### 4.2 常见问题

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 返回 404 | 用了 `manager/elective/*` 路径 | 真实路径是 `manager/electcourse/` |
| 返回「错误提示」 | 不在选课时间内 | 正常现象，展示提示文案 |
| `studentSelectCrgroupList.do` 空响应 | 缺少 `yearId`/`termId` | 从上下文补全参数 |
| 中文乱码 | 编码判断错误 | 入口页 GBK、分组页 UTF-8 |

### 4.3 伦理与合规提醒

- 教务系统的选课规则（容量、时间窗、优先级）是教学管理的一部分，
  不要用程序绕过；
- 不要实现自动刷课、占位抢课等影响其他同学的功能；
- 建议只做「查询与展示」，把选课/退课操作留给教务系统原生界面。
