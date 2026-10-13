package middleware

import (
	"net/http"

	"github.com/gin-gonic/gin"
)

// MaxRequestBodyBytes 限制请求体大小：反馈正文上限 2000 字，
// 32KB 足以容纳全部合法请求，同时避免超长载荷占用内存。
const MaxRequestBodyBytes int64 = 32 * 1024

// BodyLimit 拦截超长请求体。
func BodyLimit(limit int64) gin.HandlerFunc {
	return func(c *gin.Context) {
		if c.Request.Body != nil {
			c.Request.Body = http.MaxBytesReader(c.Writer, c.Request.Body, limit)
		}
		c.Next()
	}
}
