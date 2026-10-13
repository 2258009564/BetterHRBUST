package middleware

import (
	"net/http"
	"strings"

	"github.com/gin-gonic/gin"
)

// CORS 允许 admin 端（独立部署、非同源）在开发期直接调用接口。
// 允许来源由 CORS_ORIGINS 控制，默认仅本地开发端口。
//
// 桌面端（Tauri）webview 的页面源固定为 tauri.localhost（Windows/Linux）
// 或 tauri://localhost（macOS），浏览器不会为普通网站产生这些源，
// 无条件放行只服务于桌面客户端的意见反馈提交，不扩大网页端滥用面。
var desktopWebviewOrigins = map[string]struct{}{
	"http://tauri.localhost":  {},
	"https://tauri.localhost": {},
	"tauri://localhost":       {},
}

func CORS(origins []string) gin.HandlerFunc {
	allowAll := len(origins) == 1 && origins[0] == "*"
	allowed := make(map[string]struct{}, len(origins))
	for _, o := range origins {
		allowed[strings.ToLower(strings.TrimSpace(o))] = struct{}{}
	}

	return func(c *gin.Context) {
		origin := c.GetHeader("Origin")
		if origin != "" {
			lowerOrigin := strings.ToLower(origin)
			if allowAll {
				c.Header("Access-Control-Allow-Origin", "*")
			} else if _, ok := allowed[lowerOrigin]; ok {
				c.Header("Access-Control-Allow-Origin", origin)
				c.Header("Vary", "Origin")
				c.Header("Access-Control-Allow-Credentials", "true")
			} else if _, ok := desktopWebviewOrigins[lowerOrigin]; ok {
				c.Header("Access-Control-Allow-Origin", origin)
				c.Header("Vary", "Origin")
			}
		}
		c.Header("Access-Control-Allow-Methods", "GET, POST, PATCH, DELETE, OPTIONS")
		c.Header("Access-Control-Allow-Headers", "Content-Type, Authorization")
		c.Header("Access-Control-Max-Age", "600")

		if c.Request.Method == http.MethodOptions {
			c.AbortWithStatus(http.StatusNoContent)
			return
		}
		c.Next()
	}
}
