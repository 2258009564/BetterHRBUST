package middleware

import (
	"net/http"
	"strings"

	"github.com/gin-gonic/gin"

	"github.com/Glassous/BetterHRBUST/server/internal/model"
	"github.com/Glassous/BetterHRBUST/server/internal/pkg/jwtutil"
	"github.com/Glassous/BetterHRBUST/server/internal/pkg/response"
	"github.com/Glassous/BetterHRBUST/server/internal/repository"
)

// ContextAdminKey 是鉴权通过后写入上下文的管理员对象键名。
const ContextAdminKey = "currentAdmin"

// RequireAuth 校验 Bearer 令牌，并实时比对账号状态与令牌版本：
// 管理员被暂停或已改密时，旧令牌立即失效。
func RequireAuth(secret string, admins *repository.AdminRepository) gin.HandlerFunc {
	return func(c *gin.Context) {
		header := c.GetHeader("Authorization")
		token := strings.TrimSpace(strings.TrimPrefix(header, "Bearer "))
		if token == "" || token == header {
			response.Fail(c, http.StatusUnauthorized, response.CodeUnauthorized, "请先登录管理端")
			return
		}

		claims, err := jwtutil.Parse(secret, token)
		if err != nil {
			response.Fail(c, http.StatusUnauthorized, response.CodeUnauthorized, "登录状态已失效，请重新登录")
			return
		}

		admin, err := admins.FindByID(c.Request.Context(), claims.Subject)
		if err != nil {
			response.Fail(c, http.StatusUnauthorized, response.CodeUnauthorized, "登录状态已失效，请重新登录")
			return
		}
		if admin.Status != model.StatusActive {
			response.Fail(c, http.StatusForbidden, response.CodeAccountSuspended, "账号已被暂停，请联系超级管理员")
			return
		}
		if admin.TokenVersion != claims.TokenVersion {
			response.Fail(c, http.StatusUnauthorized, response.CodeUnauthorized, "登录状态已失效，请重新登录")
			return
		}

		c.Set(ContextAdminKey, admin)
		c.Next()
	}
}

// RequireSuper 限定仅超级管理员可用。
func RequireSuper() gin.HandlerFunc {
	return func(c *gin.Context) {
		admin, ok := CurrentAdmin(c)
		if !ok {
			response.Fail(c, http.StatusUnauthorized, response.CodeUnauthorized, "请先登录管理端")
			return
		}
		if admin.Role != model.RoleSuper {
			response.Fail(c, http.StatusForbidden, response.CodeForbidden, "仅超级管理员可执行该操作")
			return
		}
		c.Next()
	}
}

// CurrentAdmin 从上下文中取出当前管理员。
func CurrentAdmin(c *gin.Context) (*model.Admin, bool) {
	value, exists := c.Get(ContextAdminKey)
	if !exists {
		return nil, false
	}
	admin, ok := value.(*model.Admin)
	return admin, ok
}
