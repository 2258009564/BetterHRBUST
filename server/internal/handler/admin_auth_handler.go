package handler

import (
	"errors"
	"net/http"
	"strings"

	"github.com/gin-gonic/gin"

	"github.com/Glassous/BetterHRBUST/server/internal/middleware"
	"github.com/Glassous/BetterHRBUST/server/internal/pkg/response"
	"github.com/Glassous/BetterHRBUST/server/internal/service"
)

// AdminAuthHandler 处理管理端登录与口令相关接口。
type AdminAuthHandler struct {
	admins *service.AdminService
}

// NewAdminAuthHandler 构造鉴权处理器。
func NewAdminAuthHandler(admins *service.AdminService) *AdminAuthHandler {
	return &AdminAuthHandler{admins: admins}
}

type loginRequest struct {
	Username string `json:"username"`
	Password string `json:"password"`
}

// Login POST /api/admin/auth/login
func (h *AdminAuthHandler) Login(c *gin.Context) {
	var req loginRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		response.Fail(c, http.StatusBadRequest, response.CodeInvalidParam, "请输入用户名与密码")
		return
	}
	req.Username = strings.TrimSpace(req.Username)
	if req.Username == "" || req.Password == "" {
		response.Fail(c, http.StatusBadRequest, response.CodeInvalidParam, "请输入用户名与密码")
		return
	}

	result, err := h.admins.Login(c.Request.Context(), req.Username, req.Password)
	if err != nil {
		switch {
		case errors.Is(err, service.ErrAccountSuspended):
			response.Fail(c, http.StatusForbidden, response.CodeAccountSuspended, "该账号已被暂停，请联系超级管理员")
		case errors.Is(err, service.ErrInvalidCredentials):
			response.Fail(c, http.StatusUnauthorized, response.CodeInvalidCredential, "用户名或密码错误")
		default:
			response.Fail(c, http.StatusInternalServerError, response.CodeInternal, "登录失败，请稍后重试")
		}
		return
	}

	response.OK(c, gin.H{
		"token": result.Token,
		"user":  result.Admin,
	})
}

// Me GET /api/admin/auth/me
func (h *AdminAuthHandler) Me(c *gin.Context) {
	admin, ok := middleware.CurrentAdmin(c)
	if !ok {
		response.Fail(c, http.StatusUnauthorized, response.CodeUnauthorized, "请先登录管理端")
		return
	}
	response.OK(c, gin.H{"user": admin})
}

type changePasswordRequest struct {
	OldPassword string `json:"oldPassword"`
	NewPassword string `json:"newPassword"`
}

// ChangePassword POST /api/admin/auth/change-password
func (h *AdminAuthHandler) ChangePassword(c *gin.Context) {
	admin, ok := middleware.CurrentAdmin(c)
	if !ok {
		response.Fail(c, http.StatusUnauthorized, response.CodeUnauthorized, "请先登录管理端")
		return
	}

	var req changePasswordRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		response.Fail(c, http.StatusBadRequest, response.CodeInvalidParam, "参数格式不正确")
		return
	}

	updated, err := h.admins.ChangePassword(c.Request.Context(), admin.ID.Hex(), req.OldPassword, req.NewPassword)
	if err != nil {
		switch {
		case errors.Is(err, service.ErrOldPasswordWrong):
			response.Fail(c, http.StatusBadRequest, response.CodePasswordMismatch, "原密码不正确")
		case errors.Is(err, service.ErrSamePassword):
			response.Fail(c, http.StatusBadRequest, response.CodePasswordTooWeak, "新密码不能与原密码相同")
		case errors.Is(err, service.ErrPasswordTooWeak):
			response.Fail(c, http.StatusBadRequest, response.CodePasswordTooWeak, "新密码至少 8 位")
		default:
			response.Fail(c, http.StatusInternalServerError, response.CodeInternal, "修改失败，请稍后重试")
		}
		return
	}

	response.OK(c, gin.H{
		"user":    updated,
		"message": "密码已更新，请使用新密码重新登录",
	})
}
