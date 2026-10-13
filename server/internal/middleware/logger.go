// Package middleware 提供服务端中间件。
package middleware

import (
	"log"
	"time"

	"github.com/gin-gonic/gin"
)

// PrivacyLogger 记录访问日志，但**严格不记录** IP、User-Agent、
// 请求体、查询串与 Cookie —— 避免任何学生身份信息进入日志文件。
func PrivacyLogger() gin.HandlerFunc {
	return func(c *gin.Context) {
		start := time.Now()
		c.Next()
		log.Printf("[access] %s %s %d %s",
			c.Request.Method,
			c.Request.URL.Path,
			c.Writer.Status(),
			time.Since(start).Round(time.Millisecond),
		)
	}
}

// Recovery 捕获 panic，仅打印错误本身，不打印请求载荷。
func Recovery() gin.HandlerFunc {
	return func(c *gin.Context) {
		defer func() {
			if err := recover(); err != nil {
				log.Printf("[panic] %s %s: %v", c.Request.Method, c.Request.URL.Path, err)
				c.AbortWithStatusJSON(500, gin.H{
					"code":    5000,
					"message": "服务内部错误",
				})
			}
		}()
		c.Next()
	}
}
