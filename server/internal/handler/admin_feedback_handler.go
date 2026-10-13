package handler

import (
	"errors"
	"log"
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

type replyRequest struct {
	// To 可留空：留空时由服务端从反馈的联系方式中提取收件邮箱
	To      string `json:"to"`
	Subject string `json:"subject"`
	Body    string `json:"body"`
}

// Reply POST /api/admin/feedback/:id/reply —— 通过 SMTP 回复反馈留下或提取到的邮箱
func (h *AdminFeedbackHandler) Reply(c *gin.Context) {
	admin, ok := middleware.CurrentAdmin(c)
	if !ok {
		response.Fail(c, http.StatusUnauthorized, response.CodeUnauthorized, "请先登录管理端")
		return
	}

	var req replyRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		response.Fail(c, http.StatusBadRequest, response.CodeInvalidParam, "参数格式不正确")
		return
	}

	item, err := h.feedback.Reply(c.Request.Context(), c.Param("id"), service.ReplyInput{
		To:      req.To,
		Subject: req.Subject,
		Body:    req.Body,
	}, admin)
	if err != nil {
		respondReplyError(c, err)
		return
	}

	// 日志只记录反馈 ID 与管理员，不打印收件邮箱与正文，避免邮件内容进入服务端日志。
	log.Printf("[feedback] 管理员 %s 已回复反馈 %s", admin.Username, c.Param("id"))
	response.OK(c, gin.H{"item": item, "message": "回复邮件已发送"})
}

// ReplyTemplates GET /api/admin/reply-templates —— 下发快捷回复文案、发信能力与发件人前缀
func (h *AdminFeedbackHandler) ReplyTemplates(c *gin.Context) {
	response.OK(c, gin.H{
		"templates":   h.feedback.ReplyTemplates(),
		"mailEnabled": h.feedback.MailEnabled(),
		"fromName":    h.feedback.SenderName(),
	})
}

// respondReplyError 把回复链路的业务错误映射为响应码与面向管理员的提示。
func respondReplyError(c *gin.Context, err error) {
	switch {
	case errors.Is(err, repository.ErrNotFound):
		response.Fail(c, http.StatusNotFound, response.CodeNotFound, "反馈不存在或已被删除")
	case errors.Is(err, service.ErrNoReplyTarget):
		response.Fail(c, http.StatusBadRequest, response.CodeNoReplyTarget, "请填写收件邮箱：该反馈的联系方式中没有可识别的邮箱")
	case errors.Is(err, service.ErrInvalidReplyTarget):
		response.Fail(c, http.StatusBadRequest, response.CodeInvalidReplyTarget, "收件邮箱格式不正确，请检查后重试")
	case errors.Is(err, service.ErrMailNotConfigured):
		response.Fail(c, http.StatusServiceUnavailable, response.CodeMailNotConfigured, "服务端尚未配置邮件服务（SMTP），请联系超级管理员")
	case errors.Is(err, service.ErrSubjectRequired):
		response.Fail(c, http.StatusBadRequest, response.CodeInvalidParam, "请填写邮件标题")
	case errors.Is(err, service.ErrSubjectTooLong):
		response.Fail(c, http.StatusBadRequest, response.CodeInvalidParam, "邮件标题过长，请精简后重试")
	case errors.Is(err, service.ErrReplyBodyTooShort):
		response.Fail(c, http.StatusBadRequest, response.CodeInvalidParam, "请填写回复正文")
	case errors.Is(err, service.ErrReplyBodyTooLong):
		response.Fail(c, http.StatusBadRequest, response.CodeInvalidParam, "回复正文过长，请精简后重试")
	case errors.Is(err, service.ErrReplySendFailed):
		response.Fail(c, http.StatusBadGateway, response.CodeMailSendFailed, "邮件发送失败："+err.Error())
	default:
		response.Fail(c, http.StatusInternalServerError, response.CodeInternal, "回复失败，请稍后重试")
	}
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
