package middleware

import (
	"net/http"
	"strconv"
	"strings"

	"github.com/gin-gonic/gin"
)

// 桌面端（Tauri）页面源的放行区间。
//
// 生产环境下桌面端窗口加载的不是 tauri:// 协议资源，而是内置 Rust 本地服务
// http://127.0.0.1:<port>（见 desktop-tauri/src-tauri/src/lib.rs 的
// WebviewUrl::External），端口从 1950 起、被占用时顺延至多 10 个
// （desktop-tauri/src-tauri/src/server.rs 的 PORT_BASE / PORT_TRIES，与
// capabilities/main.json 的 remote.urls 同源），因此需要覆盖整段回环端口。
//
// 浏览器不会为普通网站产生回环源的 Origin，放行只服务于桌面客户端的匿名
// 意见反馈提交；这些源不给凭证（见下方 Allow-Credentials 的分支），
// 不扩大网页端滥用面。
const (
	desktopOriginPrefix = "http://127.0.0.1:"
	desktopOriginPort   = 1950 // server::PORT_BASE
	desktopOriginTries  = 10   // server::PORT_TRIES
)

var desktopAppOrigins = func() map[string]struct{} {
	origins := make(map[string]struct{}, desktopOriginTries)
	for port := desktopOriginPort; port < desktopOriginPort+desktopOriginTries; port++ {
		origins[desktopOriginPrefix+strconv.Itoa(port)] = struct{}{}
	}
	return origins
}()

// CORS 允许 admin 端（独立部署、非同源）在开发期直接调用接口。
// 允许来源由 CORS_ORIGINS 控制，默认仅本地开发端口。

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
			} else if _, ok := desktopAppOrigins[lowerOrigin]; ok {
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
