// Package response 提供统一的 JSON 响应封装。
//
// 前端只依赖 code 做分支判断，message 仅用于展示，便于后续本地化。
package response

import "github.com/gin-gonic/gin"

const (
	CodeOK = 0

	CodeInvalidParam     = 1001
	CodeRateLimited      = 1002
	CodeContentTooShort  = 1003
	CodeContentTooLong   = 1004
	CodeFieldTooLong     = 1005
	CodeUsernameTaken    = 1006
	CodePasswordTooWeak  = 1007
	CodePasswordMismatch = 1008
	// 邮件回复相关：未配置 SMTP / 无法投递 / 缺少收件邮箱 / 收件邮箱格式错误。
	CodeMailNotConfigured  = 1009
	CodeMailSendFailed     = 1010
	CodeNoReplyTarget      = 1011
	CodeInvalidReplyTarget = 1012

	CodeUnauthorized        = 2001
	CodeForbidden           = 2002
	CodePasswordChangeFirst = 2003
	CodeAccountSuspended    = 2004
	CodeInvalidCredential   = 2005

	CodeNotFound = 3001

	CodeInternal = 5000
)

// Body 是所有接口的统一响应结构。
type Body struct {
	Code    int    `json:"code"`
	Message string `json:"message"`
	Data    any    `json:"data,omitempty"`
}

// OK 返回成功响应。
func OK(c *gin.Context, data any) {
	c.JSON(200, Body{Code: CodeOK, Message: "ok", Data: data})
}

// Fail 返回失败响应：HTTP 状态码与业务码分离，便于前端统一处理。
func Fail(c *gin.Context, httpStatus, code int, message string) {
	c.AbortWithStatusJSON(httpStatus, Body{Code: code, Message: message})
}
