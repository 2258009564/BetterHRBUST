# 11 · 账户管理

本模块包含修改密码相关的接口。

---

## 接口清单

| 接口 | 方法 | 路径 | 说明 |
| --- | --- | --- | --- |
| 修改密码页 | GET | `sysmgr/user_password.jsdo` | 表单页 |
| 提交修改 | POST | `sysmgr/modifypasswd_user.jsdo` | **会真实修改密码** |

---

## 1. 修改密码页

```
GET /academic/sysmgr/user_password.jsdo
```

| 项目 | 值 |
| --- | --- |
| 状态 | `200` |
| 编码 | GBK |
| 标题 | 修改密码 |
| 大小 | 约 6.6 KB |

### 1.1 表单结构

```html
<form action="./modifypasswd_user.jsdo" method="post" target="_top">
  <table>
    <tr>
      <td>原密码</td>
      <td><input type="password" name="oldpasswd" value=""></td>
      <td rowspan="3">
        <input type="submit" name="ff" value="修 改" class="button">
        <input type="hidden" name="gotoUrl" value="null">
      </td>
    </tr>
    <tr>
      <td>新密码</td>
      <td>
        <input type="password" name="newpasswd" value="">
        <input type="hidden" name="code" value="">
      </td>
    </tr>
    <tr>
      <td>确认新密码</td>
      <td><input type="password" name="confirmedpasswd" value=""></td>
    </tr>
  </table>
</form>
```

### 1.2 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `oldpasswd` | password | 原密码 |
| `newpasswd` | password | 新密码 |
| `confirmedpasswd` | password | 确认新密码 |
| `ff` | **submit** | 提交按钮，值固定为 `修 改`（中间有一个空格） |
| `gotoUrl` | hidden | 固定值 `null` |
| `code` | hidden | 空值 |

> ⚠️ **`ff` 是提交按钮而不是普通字段。** 它的值 `修 改`（注意中间的空格）
> 会随表单一起提交。如果手工构造请求，需要原样带上 `ff=修 改`，
> 否则服务端可能无法识别提交动作。

> `target="_top"` 表示提交后会替换整个 frameset 页面——
> 说明改密成功后页面结构会变化，不要依赖原有的框架结构。

### 1.3 新密码规则

服务端对新密码有校验（长度、复杂度等），但规则未在页面中给出。
建议在客户端做基础校验（如两次输入一致），最终以服务端返回为准。

---

## 2. 提交修改

```
POST /academic/sysmgr/modifypasswd_user.jsdo
Content-Type: application/x-www-form-urlencoded
```

| 字段 | 必需 | 说明 |
| --- | --- | --- |
| `oldpasswd` | 是 | 原密码 |
| `newpasswd` | 是 | 新密码 |
| `confirmedpasswd` | 是 | 确认新密码 |
| `ff` | 是 | 固定 `修 改` |
| `gotoUrl` | 否 | 固定 `null` |
| `code` | 否 | 空值 |

> ⚠️ **这个接口会真实修改密码。** 实现时必须：
>
> - 要求用户输入原密码（服务端会校验，但不能只依赖客户端）；
> - 两次新密码一致的校验放在客户端先做一遍，减少无效请求；
> - **修改成功后必须让用户重新登录**——旧密码已失效，会话状态可能不一致；
> - 失败时如实展示服务端返回的原因（原密码错误 / 新密码不合规等）。
>
> 探测工具默认不请求此类接口。

---

## 3. 实现建议

### 3.1 是否应该实现这个功能

改密属于**低频率、高影响**的操作——出错会导致用户无法登录。
建议：

| 方案 | 说明 |
| --- | --- |
| **推荐**：跳转教务系统 | 只提供入口链接，改密在本系统内完成，风险最低 |
| 自实现 | 必须做完整的错误处理与二次确认 |
| 不建议 | 在客户端保存密码、自动填充、记住原密码 |

如果自实现，**不要在本地保存任何密码明文**，包括「记住原密码」这类便利功能。

### 3.2 交互建议

```
1. 用户输入原密码 + 新密码 + 确认新密码
2. 客户端校验两次新密码一致（不一致直接提示，不发请求）
3. 二次确认弹窗：「确认修改密码？修改后需要使用新密码重新登录」
4. 提交请求
5. 成功后：清除本地会话与本地的任何凭证缓存，跳转到登录页
6. 失败后：展示服务端原因，保留用户输入（除密码字段）
```

### 3.3 常见问题

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 提交后无反应 | 缺少 `ff=修 改` 字段 | 原样带上该字段 |
| 返回「提示信息」错误页 | 原密码错误或新密码不合规 | 展示服务端提示 |
| 修改成功后行为异常 | 会话已失效 | 清除会话并跳转登录页 |
| 中文乱码 | 按 UTF-8 解码了 | 本模块是 GBK |
