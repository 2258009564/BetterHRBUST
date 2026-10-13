// Package router 负责路由注册与中间件装配。
package router

import (
	"bytes"
	"context"
	"encoding/json"
	"io"
	"log"
	"strings"
	"time"

	"github.com/gin-gonic/gin"

	"github.com/Glassous/BetterHRBUST/server/internal/config"
	"github.com/Glassous/BetterHRBUST/server/internal/handler"
	"github.com/Glassous/BetterHRBUST/server/internal/middleware"
	"github.com/Glassous/BetterHRBUST/server/internal/repository"
	"github.com/Glassous/BetterHRBUST/server/internal/service"
)

// 限流档位：反馈提交按来源限制分钟级与天级频次；管理端登录按来源+用户名限制爆破。
var (
	// FeedbackRules 反馈提交限流：10 分钟内 5 条，24 小时内 30 条。
	FeedbackRules = []middleware.RateLimitRule{
		{Limit: 5, Window: 10 * time.Minute},
		{Limit: 30, Window: 24 * time.Hour},
	}
	// LoginRules 管理端登录限流：15 分钟内 10 次。
	LoginRules = []middleware.RateLimitRule{
		{Limit: 10, Window: 15 * time.Minute},
	}
)

// Deps 路由依赖集合。
type Deps struct {
	Config       config.Config
	Feedback     *service.FeedbackService
	Admins       *service.AdminService
	AdminRepo    *repository.AdminRepository
	Limiter      *middleware.RateLimiter
	LoginLimiter *middleware.RateLimiter
	// HealthCheck 探活依赖，为空时 /api/healthz 仅返回进程存活。
	HealthCheck func(ctx context.Context) error
}

// New 构建 Gin 引擎。
func New(deps Deps) *gin.Engine {
	gin.SetMode(gin.ReleaseMode)

	engine := gin.New()

	// 反向代理头仅在显式配置 TRUSTED_PROXIES 时可信，
	// 否则直接使用连接来源地址，避免客户端伪造 X-Forwarded-For 绕过限流。
	if len(deps.Config.TrustedProxies) == 0 {
		_ = engine.SetTrustedProxies(nil)
	} else if err := engine.SetTrustedProxies(deps.Config.TrustedProxies); err != nil {
		log.Printf("[router] TRUSTED_PROXIES 配置无效，已回退为不信任任何代理: %v", err)
		_ = engine.SetTrustedProxies(nil)
	}

	engine.Use(middleware.Recovery())
	engine.Use(middleware.PrivacyLogger())
	engine.Use(middleware.BodyLimit(middleware.MaxRequestBodyBytes))
	engine.Use(middleware.CORS(deps.Config.CORSOrigins))

	publicHandler := handler.NewPublicHandler(deps.Feedback, deps.HealthCheck)
	authHandler := handler.NewAdminAuthHandler(deps.Admins)
	userHandler := handler.NewAdminUserHandler(deps.Admins)
	feedbackHandler := handler.NewAdminFeedbackHandler(deps.Feedback)

	api := engine.Group("/api")

	// ---------------- 公开接口 ----------------
	api.GET("/healthz", publicHandler.Health)
	api.POST("/feedback",
		deps.Limiter.Middleware("feedback", nil),
		publicHandler.SubmitFeedback,
	)

	// ---------------- 管理端接口 ----------------
	adminGroup := api.Group("/admin")
	adminGroup.POST("/auth/login",
		deps.LoginLimiter.Middleware("login", loginUsernameKey),
		authHandler.Login,
	)

	authorized := adminGroup.Group("")
	authorized.Use(middleware.RequireAuth(deps.Config.JWTSecret, deps.AdminRepo))
	authorized.GET("/auth/me", authHandler.Me)
	authorized.POST("/auth/change-password", authHandler.ChangePassword)

	authorized.GET("/feedback", feedbackHandler.List)
	authorized.GET("/feedback/:id", feedbackHandler.Detail)
	authorized.PATCH("/feedback/:id/read", feedbackHandler.SetRead)
	authorized.POST("/feedback/:id/reply", feedbackHandler.Reply)
	authorized.DELETE("/feedback/:id", feedbackHandler.Delete)
	// 快捷回复文案独立成枝，避免与 /feedback/:id 在同一层级产生路由冲突
	authorized.GET("/reply-templates", feedbackHandler.ReplyTemplates)

	superOnly := authorized.Group("")
	superOnly.Use(middleware.RequireSuper())
	superOnly.GET("/users", userHandler.List)
	superOnly.POST("/users", userHandler.Create)
	superOnly.PATCH("/users/:id/status", userHandler.SetStatus)

	return engine
}

// loginUsernameKey 从登录请求体中提取用户名用于限流键。
// 读取后立即回填请求体，保证后续绑定不受影响；请求体已由 BodyLimit 限制大小。
func loginUsernameKey(c *gin.Context) string {
	if c.Request.Body == nil {
		return ""
	}
	body, err := io.ReadAll(c.Request.Body)
	if err != nil {
		return ""
	}
	c.Request.Body = io.NopCloser(bytes.NewReader(body))

	var payload struct {
		Username string `json:"username"`
	}
	if err := json.Unmarshal(body, &payload); err != nil {
		return ""
	}
	return strings.ToLower(strings.TrimSpace(payload.Username))
}
