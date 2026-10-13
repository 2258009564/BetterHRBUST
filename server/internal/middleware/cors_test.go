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

func TestCORSAllowsDesktopLoopbackOrigins(t *testing.T) {
	// 桌面端页面源是内置本地服务的回环地址：1950 起、占用顺延至多 10 个
	// （capabilities/main.json 的 remote.urls 即同一区间），名单必须覆盖整段；
	// 区间外、localhost 形态与 tauri:// 协议形态都不属于本应用的真实来源。
	for _, tc := range []struct {
		origin string
		allow  bool
	}{
		{"http://127.0.0.1:1950", true},
		{"http://127.0.0.1:1955", true},
		{"http://127.0.0.1:1959", true},
		{"http://127.0.0.1:1949", false},
		{"http://127.0.0.1:1960", false},
		{"http://localhost:1950", false},
		{"http://tauri.localhost", false},
		{"tauri://localhost", false},
	} {
		router := setupCORSRouter(nil)

		res := httptest.NewRecorder()
		req := httptest.NewRequest(http.MethodPost, "/feedback", nil)
		req.Header.Set("Origin", tc.origin)
		router.ServeHTTP(res, req)

		want := ""
		if tc.allow {
			want = tc.origin
		}
		if got := res.Header().Get("Access-Control-Allow-Origin"); got != want {
			t.Fatalf("来源 %q 的 Allow-Origin 应为 %q，实际: %q", tc.origin, want, got)
		}
		// 匿名提交链路，无需放行凭证
		if got := res.Header().Get("Access-Control-Allow-Credentials"); got != "" {
			t.Fatalf("桌面端来源 %q 不应放行凭证，实际: %q", tc.origin, got)
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
	req.Header.Set("Origin", "http://127.0.0.1:1950")
	router.ServeHTTP(res, req)

	if res.Code != http.StatusNoContent {
		t.Fatalf("预检请求应返回 204，实际: %d", res.Code)
	}
	if got := res.Header().Get("Access-Control-Allow-Origin"); got != "http://127.0.0.1:1950" {
		t.Fatalf("预检也应放行桌面端来源，实际: %q", got)
	}
}
