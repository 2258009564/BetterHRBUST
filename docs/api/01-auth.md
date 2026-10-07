# 01 · 认证、会话与框架

本模块负责「进入系统」：验证码、登录、退出，以及登录后的页面框架结构、
菜单获取和模块调度机制。

模块调度（`accessModule.do`）虽然属于框架层，但它是**发现其他所有接口的前提**，
务必先理解 §4。

---

## 接口清单

| 接口 | 方法 | 路径 | 说明 |
| --- | --- | --- | --- |
| 站点入口 | GET | `/academic/` | 跳转到登录页，并取得会话 Cookie |
| 获取验证码 | GET | `getCaptcha.do` | 返回验证码图片（JPEG） |
| 校验验证码 | POST | `checkCaptcha.do` | 返回 `true` / `false` |
| 登录提交 | POST | `j_acegi_security_check` | Acegi 表单认证 |
| 退出登录 | GET | `j_acegi_logout` | 销毁会话 |
| 左侧菜单树 | GET | `listLeft.do` | 菜单、教学周、全部 `moduleId` |
| 按类型筛选菜单 | GET | `listLeft.do?moduleType=` | 按权限类别枚举菜单 |
| 顶部导航页 | GET | `top.jsp` | 页面顶部，含退出入口 |
| 框架主容器 | GET | `main.jsp` | frameset 容器 |
| 门户页 | GET | `index_new.jsp` | **登录后真正的入口页** |
| 主框架页 | GET | `frameset_index.jsp` | 定义四个 frame 的地址 |
| 主框架默认页 | GET | `checkPassword.do` | 默认加载内容（教学运行公告） |
| 底部框架页 | GET | `footer.jsp` | 页脚 |
| 顶部信息片段 | GET | `showHeader.do` | 顶栏 HTML 片段 |
| 模块调度 | GET | `accessModule.do?moduleId=` | **302 揭示真实功能页地址** |

另有 6 个 DWR 相关接口，见 §5。

---

## 1. 认证接口

### 1.1 站点入口

```
GET /academic/
```

| 项目 | 值 |
| --- | --- |
| 请求参数 | 无 |
| 响应状态 | `302` |
| 跳转目标 | `/academic/common/security/login.jsp` |

用于建立会话：响应会 `Set-Cookie: JSESSIONID=...`，后续所有请求都要带上它。
同时这也是**会话有效性判定的基准**——响应体包含登录表单特征即为未登录。

### 1.2 获取验证码

```
GET /academic/getCaptcha.do?_t=<时间戳>
```

| 参数 | 位置 | 必需 | 说明 |
| --- | --- | --- | --- |
| `_t` | query | 否 | 时间戳，用于避免缓存。刷新时应更换 |

| 项目 | 值 |
| --- | --- |
| 响应类型 | `image/jpeg` |
| 图片尺寸 | 96 × 25 |
| 内容 | 4 位数字 |
| 编码 | 不适用（二进制） |

**注意**：响应头是 `image/jpeg`，不是 PNG。保存到文件时要按 `Content-Type`
决定扩展名，否则 Windows 等系统的默认看图工具可能拒绝打开。

登录页中的原始写法是：

```html
<img id="jcaptcha" src="/academic/getCaptcha.do" width="96" height="25">
<!-- 点击刷新 -->
<script>obj.src = "/academic/getCaptcha.do?" + Math.random()</script>
```

### 1.3 校验验证码

```
POST /academic/checkCaptcha.do?captchaCode=<4位数字>
```

| 参数 | 位置 | 必需 | 说明 |
| --- | --- | --- | --- |
| `captchaCode` | query | 是 | 用户看到的 4 位数字 |

| 项目 | 值 |
| --- | --- |
| 响应类型 | `text/plain` |
| 响应内容 | `true` 或 `false` |

这是教务系统自身登录逻辑的一环——点击登录按钮时先调它，通过后才提交表单。
**必须在同一个会话内**：先取图再校验，不能跨会话。

### 1.4 登录提交

```
POST /academic/j_acegi_security_check
Content-Type: application/x-www-form-urlencoded
```

| 字段 | 必需 | 说明 |
| --- | --- | --- |
| `j_username` | 是 | 学号 |
| `j_password` | 是 | 密码，**明文提交** |
| `j_captcha` | 是 | 验证码 |

**密码不需要加密。** 站点 `styles/js/md5.js` 中的 `plaintext()` 是恒等函数：

```js
function plaintext(s, salt) { return s; }
```

登录结果判定：

| 结果 | 响应特征 |
| --- | --- |
| 成功 | HTTP `302`，`Location` 指向门户页，响应体为空 |
| 失败 | HTTP `200`，返回登录页；响应体含 `badCredentials` / `密码错误` / `用户名不存在` / `验证码错误` |

失败场景及对应文案：

| 场景 | 特征文案 |
| --- | --- |
| 密码错误 | `badCredentials`、`密码错误` |
| 用户名不存在 | `用户名不存在` |
| 验证码错误 | `验证码错误` |
| 验证码过期 | 同「验证码错误」，需重新取图 |

### 1.5 退出登录

```
GET /academic/j_acegi_logout
```

销毁服务端会话。调用后再访问受保护接口会回落登录页。

---

## 2. 菜单与导航

### 2.1 左侧菜单树

```
GET /academic/listLeft.do
```

| 项目 | 值 |
| --- | --- |
| 编码 | **GBK** |
| 响应类型 | HTML 片段 |
| 响应大小 | 约 16 KB |

这是**接口发现的主要来源**，响应中包含：

1. **当前教学周**：形如 `第6周` 的文本；
2. **全部模块入口**：约 19 个 `accessModule.do?moduleId=NNN`。

教学周的典型提取方式：

```js
const match = html.match(/第\s*(\d+)\s*周/);
const currentWeek = match ? Number(match[1]) : null;
```

> 页面内的 `<meta>` 声明是 UTF-8，但实际是 GBK，**以响应头为准**。

### 2.2 按模块类型筛选菜单

```
GET /academic/listLeft.do?moduleType=<类型>
```

| 参数 | 可选值 |
| --- | --- |
| `moduleType` | `-1`、`3`、`5`、`6`、`7`、`8` |

用于按权限类别枚举菜单，是补全模块列表的补充入口。
在 `showHeader.do` 的链接中可以找到这些取值。

### 2.3 顶部导航页

```
GET /academic/top.jsp
```

| 项目 | 值 |
| --- | --- |
| 编码 | `gb2312` |
| 响应大小 | 约 2 KB |

含退出登录入口。

### 2.4 顶部信息片段

```
GET /academic/showHeader.do?randomString=<随机串>
```

| 项目 | 值 |
| --- | --- |
| 编码 | UTF-8 |
| 响应大小 | 约 3.8 KB |

返回顶栏的 HTML 片段，含约 12 个链接。是发现以下接口的来源：

- `showPersonalInfo.do`（我的信息）
- `viewCalendarInfo.do`（教学运行公告）
- `listLeft.do?moduleType=N`

---

## 3. 门户页与框架结构

### 3.1 登录后的入口

登录成功后应请求：

```
GET /academic/index_new.jsp
```

| 项目 | 值 |
| --- | --- |
| 编码 | GBK |
| 标题 | 综合教务管理系统 |
| 响应大小 | 约 4 KB |

**`index.jsp` 会 302 回登录页，它不是登录后的入口。** 不要用它判断登录状态。

`index_new.jsp` 内部只有一个 iframe，指向真正的框架页：

```html
<iframe src="frameset_index.jsp" ...></iframe>
```

### 3.2 主框架结构

```
GET /academic/frameset_index.jsp
```

| 项目 | 值 |
| --- | --- |
| 响应大小 | 约 1.2 KB |

定义了四个 frame：

| frame | 地址 | 说明 |
| --- | --- | --- |
| `headerFrame` | `showHeader.do` | 由 `listLeft.do` 加载后指定，初始为空 |
| `menuFrame` | `listLeft.do?randomString=xxx` | 左侧菜单 |
| `mainFrame` | `./checkPassword.do` | 主内容区 |
| `footerFrame` | `footer.jsp` | 页脚 |

抓取门户页信息时需要**递归跟进 frame / iframe**，否则会漏掉框架里的功能入口。

### 3.3 主框架默认页

```
GET /academic/checkPassword.do
```

页面标题为「教学运行公告」，是 `mainFrame` 的默认内容，同时用于提醒修改密码。
响应中含约 26 个嵌套地址，可作为接口发现的补充来源。

### 3.4 其他框架页

| 接口 | 路径 | 说明 |
| --- | --- | --- |
| 框架主容器 | `main.jsp` | frameset 容器，约 367 字节，声明 `ISO-8859-1`（正文为纯 ASCII） |
| 底部框架页 | `footer.jsp` | 页脚 |

---

## 4. 模块调度

### 4.1 接口定义

```
GET /academic/accessModule.do?moduleId=<ID>&groupId=&randomString=<随机串>
```

| 参数 | 必需 | 说明 |
| --- | --- | --- |
| `moduleId` | 是 | 模块 ID，从菜单页提取 |
| `groupId` | 否 | 分组 ID，通常留空 |
| `randomString` | 是 | `yyyyMMddHHmmss` + 6 位随机字母数字 |

### 4.2 行为

响应 **302**，`Location` 即真实功能页地址：

```
accessModule.do?moduleId=210&groupId=&randomString=20261008143022aB3xY9
  → 302 → /academic/manager/studyschedule/help.htm
```

### 4.3 randomString 生成

```js
function makeRandomString() {
  const d = new Date();
  const pad = (n) => String(n).padStart(2, '0');
  const stamp =
    `${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}` +
    `${pad(d.getHours())}${pad(d.getMinutes())}${pad(d.getSeconds())}`;

  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
  let rand = '';
  for (let i = 0; i < 6; i += 1) {
    rand += chars[Math.floor(Math.random() * chars.length)];
  }
  return stamp + rand;
}
```

### 4.4 使用方式

**这是发现接口地址的正确途径。** 完整流程：

```
1. GET listLeft.do              → 提取全部 moduleId
2. 逐个 GET accessModule.do     → 跟随 302，得到真实地址
3. 对真实地址发起请求            → 分析响应，提取更深的接口
```

不要按命名规律推测路径——35 个推测路径全部返回 404，详见
[`00-overview.md` §5.3](./00-overview.md)。

参考实现：`tools/probe/lib/discover.mjs`。

---

## 5. DWR 接口

系统使用 DWR 做站内消息推送。以下接口**无业务价值**，在此仅作说明，
避免重复探索。

| 接口 | 路径 | 说明 |
| --- | --- | --- |
| 总览页 | `dwr/index.html` | 列出全部对外暴露的 Bean |
| 测试页 | `dwr/test/messagePush` | 列出该 Bean 的全部方法 |
| 测试页 | `dwr/test/pushStart` | 同上 |
| 接口描述 | `dwr/interface/messagePush.js` | JS 存根 |
| 接口描述 | `dwr/interface/pushStart.js` | JS 存根 |
| 运行时 | `dwr/engine.js` | DWR 引擎，约 78 KB |

### 5.1 暴露的 Bean

```
messagePush  (net.theol.project.eduaffair.calendar.service.MessagePush)
pushStart    (net.theol.project.eduaffair.calendar.service.PushStart)
```

### 5.2 方法清单

| Bean | 业务方法 | 其余方法 |
| --- | --- | --- |
| `messagePush` | `onPageLoad(callback)` | 全部继承自 `java.lang.Object` |
| `pushStart` | `sendMessageAuto(Integer, String)` | Struts Action 生命周期方法 + `Object` 继承方法 |

- `messagePush.onPageLoad()` —— 无业务参数，仅用于注册推送会话，
  确保服务端推送时能找到指定用户。
- `pushStart.sendMessageAuto(接收人ID, 推送内容)` —— **服务端向客户端推送消息**，
  学生侧用不到。注意 `pushStart` 实际是一个 Struts Action，被 DWR 一并暴露。

### 5.3 调用协议

```
POST /academic/dwr/call/plaincall/<bean>.<method>.dwr
Content-Type: text/plain

callCount=1
c0-scriptName=<bean>
c0-methodName=<method>
c0-id=0
batchId=0
page=/academic/xxx.jsp
scriptSessionId=<会话中的随机串>
```

### 5.4 结论

**DWR 只用于站内消息推送，不暴露成绩 / 课表 / 学籍等业务数据。**
前端功能走 `.do` / `.jsdo` 页面接口。

> 分析 `dwr/engine.js` 会扫出大量 JS 表达式形式的伪地址
> （`dwr/MSXML2.DO`、`dwr/dom.do` 等），提取接口地址时应跳过纯 `.js` 文件。

---

## 6. 实现建议

### 6.1 会话管理

开发期用代理让请求同源（见 [`00-overview.md` §8.1](./00-overview.md)）；
生产环境需要一个后端服务持有会话。参考 `tools/probe/server.mjs`。

### 6.2 登录状态检查

不要用 `index.jsp`（必然 302）。推荐用 `student/currcourse/currcourse.jsdo`：

- 返回 200 且含 `studentid` → 会话有效，同时拿到上下文参数；
- 返回登录页 → 会话失效。

一次请求同时完成「校验会话」与「获取上下文」两件事。

### 6.3 教学周的用途

`listLeft.do` 返回的「第 N 周」可用于：

- 课表默认定位到当前周；
- 结合校历（`calendar/calendarViewList.do`）把周次换算成真实日期；
- 导出的日历文件（ICS）需要开学日期时，可由「当前周 + 今天」反推。

反推公式：

```
第 1 周周一 = 本周周一 − (当前周 − 1) × 7 天
```
