package handler

import (
	"errors"
	"net/http"
	"strconv"
	"strings"

	"github.com/gin-gonic/gin"

	"github.com/Glassous/BetterHRBUST/server/internal/middleware"
	"github.com/Glassous/BetterHRBUST/server/internal/model"
	"github.com/Glassous/BetterHRBUST/server/internal/pkg/response"
	"github.com/Glassous/BetterHRBUST/server/internal/repository"
	"github.com/Glassous/BetterHRBUST/server/internal/service"
)

const (
	defaultPageSize = 20
	maxPageSize     = 100
)

// AdminFeedbackHandler 处理反馈的阅读、标记与删除。
type AdminFeedbackHandler struct {
	feedback *service.FeedbackService
}

// NewAdminFeedbackHandler 构造反馈管理处理器。
func NewAdminFeedbackHandler(feedback *service.FeedbackService) *AdminFeedbackHandler {
	return &AdminFeedbackHandler{feedback: feedback}
}

// List GET /api/admin/feedback?keyword=&state=&page=&pageSize=
func (h *AdminFeedbackHandler) List(c *gin.Context) {
	page := parsePositiveInt(c.Query("page"), 1)
	pageSize := parsePositiveInt(c.Query("pageSize"), defaultPageSize)
	if pageSize > maxPageSize {
		pageSize = maxPageSize
	}

	filter := repository.FeedbackFilter{
		Keyword:   c.Query("keyword"),
		ReadState: strings.TrimSpace(c.Query("state")),
		Limit:     int64(pageSize),
		Offset:    int64((page - 1) * pageSize),
	}

	items, total, err := h.feedback.List(c.Request.Context(), filter)
	if err != nil {
		response.Fail(c, http.StatusInternalServerError, response.CodeInternal, "加载反馈列表失败")
		return
	}

	unread, err := h.feedback.UnreadCount(c.Request.Context())
	if err != nil {
		response.Fail(c, http.StatusInternalServerError, response.CodeInternal, "加载未读数量失败")
		return
	}

	response.OK(c, gin.H{
		"items":    items,
		"total":    total,
		"page":     page,
		"pageSize": pageSize,
		"unread":   unread,
	})
}

// Detail GET /api/admin/feedback/:id
func (h *AdminFeedbackHandler) Detail(c *gin.Context) {
	item, err := h.feedback.Get(c.Request.Context(), c.Param("id"))
	if err != nil {
		response.Fail(c, http.StatusNotFound, response.CodeNotFound, "反馈不存在或已被删除")
		return
	}
	response.OK(c, gin.H{"item": item})
}

type readRequest struct {
	IsRead bool `json:"isRead"`
}

// SetRead PATCH /api/admin/feedback/:id/read
func (h *AdminFeedbackHandler) SetRead(c *gin.Context) {
	var req readRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		response.Fail(c, http.StatusBadRequest, response.CodeInvalidParam, "参数格式不正确")
		return
	}
	item, err := h.feedback.SetRead(c.Request.Context(), c.Param("id"), req.IsRead)
	if err != nil {
		response.Fail(c, http.StatusNotFound, response.CodeNotFound, "反馈不存在或已被删除")
		return
	}
	response.OK(c, gin.H{"item": item})
}

// Delete DELETE /api/admin/feedback/:id —— 仅超级管理员
func (h *AdminFeedbackHandler) Delete(c *gin.Context) {
	admin, ok := middleware.CurrentAdmin(c)
	if !ok {
		response.Fail(c, http.StatusUnauthorized, response.CodeUnauthorized, "请先登录管理端")
		return
	}
	if admin.Role != model.RoleSuper {
		response.Fail(c, http.StatusForbidden, response.CodeForbidden, "仅超级管理员可删除反馈")
		return
	}

	if err := h.feedback.Delete(c.Request.Context(), c.Param("id")); err != nil {
		if errors.Is(err, repository.ErrNotFound) {
			response.Fail(c, http.StatusNotFound, response.CodeNotFound, "反馈不存在或已被删除")
			return
		}
		response.Fail(c, http.StatusInternalServerError, response.CodeInternal, "删除失败，请稍后重试")
		return
	}
	response.OK(c, gin.H{"deleted": true})
}

func parsePositiveInt(raw string, fallback int) int {
	value, err := strconv.Atoi(strings.TrimSpace(raw))
	if err != nil || value < 1 {
		return fallback
	}
	return value
}
