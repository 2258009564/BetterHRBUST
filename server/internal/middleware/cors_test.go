package middleware

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/gin-gonic/gin"
)

func setupCORSRouter(origins []string) *gin.Engine {
	gin.SetMode(gin.TestMode)
	engine := gin.New()
	engine.Use(CORS(origins))
	engine.POST("/feedback", func(c *gin.Context) {
		c.Status(http.StatusOK)
	})
	return engine
}

func TestCORSAllowsConfiguredOrigin(t *testing.T) {
	router := setupCORSRouter([]string{"http://localhost:5173"})

	res := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodPost, "/feedback", nil)
	req.Header.Set("Origin", "http://localhost:5173")
	router.ServeHTTP(res, req)

	if got := res.Header().Get("Access-Control-Allow-Origin"); got != "http://localhost:5173" {
		t.Fatalf("名单内来源应回显 Origin，实际: %q", got)
	}
	if got := res.Header().Get("Access-Control-Allow-Credentials"); got != "true" {
		t.Fatalf("名单内来源应允许携带凭证，实际: %q", got)
	}
}

func TestCORSAllowsDesktopWebviewOrigins(t *testing.T) {
	// 未配置任何名单时桌面 webview 源仍应放行（桌面端意见反馈链路）
	for _, origin := range []string{
		"http://tauri.localhost",
		"https://tauri.localhost",
		"tauri://localhost",
	} {
		router := setupCORSRouter(nil)

		res := httptest.NewRecorder()
		req := httptest.NewRequest(http.MethodPost, "/feedback", nil)
		req.Header.Set("Origin", origin)
		router.ServeHTTP(res, req)

		if got := res.Header().Get("Access-Control-Allow-Origin"); got != origin {
			t.Fatalf("桌面 webview 源 %q 应被放行，实际 Allow-Origin: %q", origin, got)
		}
		// 匿名提交链路，无需放行凭证
		if got := res.Header().Get("Access-Control-Allow-Credentials"); got != "" {
			t.Fatalf("桌面 webview 源不应放行凭证，实际: %q", got)
		}
	}
}

func TestCORSRejectsUnknownOrigin(t *testing.T) {
	router := setupCORSRouter([]string{"http://localhost:5173"})

	res := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodPost, "/feedback", nil)
	req.Header.Set("Origin", "https://evil.example.com")
	router.ServeHTTP(res, req)

	if got := res.Header().Get("Access-Control-Allow-Origin"); got != "" {
		t.Fatalf("未知来源不应被放行，实际 Allow-Origin: %q", got)
	}
}

func TestCORSPreflightShortCircuits(t *testing.T) {
	router := setupCORSRouter([]string{"http://localhost:5173"})

	res := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodOptions, "/feedback", nil)
	req.Header.Set("Origin", "http://tauri.localhost")
	router.ServeHTTP(res, req)

	if res.Code != http.StatusNoContent {
		t.Fatalf("预检请求应返回 204，实际: %d", res.Code)
	}
	if got := res.Header().Get("Access-Control-Allow-Origin"); got != "http://tauri.localhost" {
		t.Fatalf("预检也应放行桌面 webview 源，实际: %q", got)
	}
}
