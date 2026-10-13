package handler

import (
	"errors"
	"net/http"

	"github.com/gin-gonic/gin"

	"github.com/Glassous/BetterHRBUST/server/internal/middleware"
	"github.com/Glassous/BetterHRBUST/server/internal/pkg/response"
	"github.com/Glassous/BetterHRBUST/server/internal/service"
)

// AdminUserHandler 处理管理员账号管理接口（仅超级管理员可用）。
type AdminUserHandler struct {
	admins *service.AdminService
}

// NewAdminUserHandler 构造管理员账号处理器。
func NewAdminUserHandler(admins *service.AdminService) *AdminUserHandler {
	return &AdminUserHandler{admins: admins}
}

// List GET /api/admin/users
func (h *AdminUserHandler) List(c *gin.Context) {
	items, err := h.admins.List(c.Request.Context())
	if err != nil {
		response.Fail(c, http.StatusInternalServerError, response.CodeInternal, "加载管理员列表失败")
		return
	}
	response.OK(c, gin.H{"items": items, "total": len(items)})
}

// Create POST /api/admin/users
// 初始口令自动生成并仅在本次响应中返回一次，之后数据库中只保留哈希。
func (h *AdminUserHandler) Create(c *gin.Context) {
	actor, ok := middleware.CurrentAdmin(c)
	if !ok {
		response.Fail(c, http.StatusUnauthorized, response.CodeUnauthorized, "请先登录管理端")
		return
	}

	var input service.CreateAdminInput
	if err := c.ShouldBindJSON(&input); err != nil {
		response.Fail(c, http.StatusBadRequest, response.CodeInvalidParam, "参数格式不正确")
		return
	}

	result, err := h.admins.CreateAdmin(c.Request.Context(), actor, input)
	if err != nil {
		switch {
		case errors.Is(err, service.ErrInvalidUsername):
			response.Fail(c, http.StatusBadRequest, response.CodeInvalidParam, "用户名需为 3-32 位字母、数字、下划线、点或短横线")
		case errors.Is(err, service.ErrInvalidNickname):
			response.Fail(c, http.StatusBadRequest, response.CodeInvalidParam, "昵称必填且不超过 32 个字")
		case errors.Is(err, service.ErrPasswordTooWeak):
			response.Fail(c, http.StatusBadRequest, response.CodePasswordTooWeak, "初始密码至少 8 位")
		case errors.Is(err, service.ErrUsernameTaken):
			response.Fail(c, http.StatusConflict, response.CodeUsernameTaken, "该用户名已存在")
		default:
			response.Fail(c, http.StatusInternalServerError, response.CodeInternal, "创建失败，请稍后重试")
		}
		return
	}

	response.OK(c, gin.H{
		"user":            result.Admin,
		"initialPassword": result.GeneratedPassword,
		"generated":       result.Generated,
	})
}

type statusRequest struct {
	Status string `json:"status"`
}

// SetStatus PATCH /api/admin/users/:id/status
func (h *AdminUserHandler) SetStatus(c *gin.Context) {
	actor, ok := middleware.CurrentAdmin(c)
	if !ok {
		response.Fail(c, http.StatusUnauthorized, response.CodeUnauthorized, "请先登录管理端")
		return
	}

	var req statusRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		response.Fail(c, http.StatusBadRequest, response.CodeInvalidParam, "参数格式不正确")
		return
	}

	updated, err := h.admins.SetStatus(c.Request.Context(), actor, c.Param("id"), req.Status)
	if err != nil {
		switch {
		case errors.Is(err, service.ErrCannotSuspendSelf):
			response.Fail(c, http.StatusBadRequest, response.CodeForbidden, "不能暂停自己的账号")
		case errors.Is(err, service.ErrSuperNotSuspendable):
			response.Fail(c, http.StatusBadRequest, response.CodeForbidden, "超级管理员账号不可被暂停")
		case errors.Is(err, service.ErrNotFound):
			response.Fail(c, http.StatusNotFound, response.CodeNotFound, "管理员不存在")
		default:
			response.Fail(c, http.StatusInternalServerError, response.CodeInternal, "操作失败，请稍后重试")
		}
		return
	}

	response.OK(c, gin.H{"user": updated})
}
