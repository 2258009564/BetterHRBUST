// Package handler 负责 HTTP 层参数绑定与错误到响应码的映射。
package handler

import (
	"context"
	"errors"
	"log"
	"net/http"
	"time"

	"github.com/gin-gonic/gin"

	"github.com/Glassous/BetterHRBUST/server/internal/pkg/response"
	"github.com/Glassous/BetterHRBUST/server/internal/service"
)

// PublicHandler 提供无需鉴权的接口。
type PublicHandler struct {
	feedback *service.FeedbackService
	// healthCheck 探活依赖（MongoDB ping），容器 healthcheck 使用。
	healthCheck func(ctx context.Context) error
}

// NewPublicHandler 构造公开接口处理器。
func NewPublicHandler(feedback *service.FeedbackService, healthCheck func(ctx context.Context) error) *PublicHandler {
	return &PublicHandler{feedback: feedback, healthCheck: healthCheck}
}

// SubmitFeedback POST /api/feedback
//
// 请求体只接受 content / contact / signature / anonymous 四个字段，
// 服务端不读取任何其他字段，也不记录来源信息。
func (h *PublicHandler) SubmitFeedback(c *gin.Context) {
	var input service.FeedbackSubmitInput
	if err := c.ShouldBindJSON(&input); err != nil {
		response.Fail(c, http.StatusBadRequest, response.CodeInvalidParam, "提交内容格式不正确")
		return
	}

	feedback, err := h.feedback.Submit(c.Request.Context(), input)
	if err != nil {
		writeFeedbackError(c, err)
		return
	}

	response.OK(c, gin.H{
		"id":        feedback.ID.Hex(),
		"createdAt": feedback.CreatedAt,
	})
}

// Health GET /api/healthz —— 依赖不可用时返回 503，供容器与负载均衡探活。
func (h *PublicHandler) Health(c *gin.Context) {
	if h.healthCheck != nil {
		ctx, cancel := context.WithTimeout(c.Request.Context(), 3*time.Second)
		defer cancel()
		if err := h.healthCheck(ctx); err != nil {
			c.JSON(http.StatusServiceUnavailable, response.Body{Code: response.CodeInternal, Message: "依赖服务不可用"})
			return
		}
	}
	response.OK(c, gin.H{"status": "ok"})
}

// writeFeedbackError 将业务错误映射为统一响应。
func writeFeedbackError(c *gin.Context, err error) {
	switch {
	case errors.Is(err, service.ErrContentTooShort):
		response.Fail(c, http.StatusBadRequest, response.CodeContentTooShort, "反馈内容太短，请至少填写 5 个字")
	case errors.Is(err, service.ErrContentTooLong):
		response.Fail(c, http.StatusBadRequest, response.CodeContentTooLong, "反馈内容过长，请控制在 2000 字以内")
	case errors.Is(err, service.ErrContactTooLong):
		response.Fail(c, http.StatusBadRequest, response.CodeFieldTooLong, "联系方式过长，请控制在 128 字以内")
	case errors.Is(err, service.ErrSignatureTooLong):
		response.Fail(c, http.StatusBadRequest, response.CodeFieldTooLong, "姓名过长，请控制在 64 字以内")
	default:
		log.Printf("[feedback] 写入失败: %v", err)
		response.Fail(c, http.StatusInternalServerError, response.CodeInternal, "提交失败，请稍后重试")
	}
}
