// Package service 承载业务规则与输入校验。
package service

import (
	"context"
	"errors"
	"strings"
	"unicode"
	"unicode/utf8"

	"github.com/Glassous/BetterHRBUST/server/internal/model"
	"github.com/Glassous/BetterHRBUST/server/internal/repository"
)

// 输入长度限制（按字符数，非字节数）。
const (
	MinContentLength   = 5
	MaxContentLength   = 2000
	MaxContactLength   = 128
	MaxSignatureLength = 64
)

// 反馈提交相关的业务错误。
var (
	ErrContentTooShort  = errors.New("content too short")
	ErrContentTooLong   = errors.New("content too long")
	ErrContactTooLong   = errors.New("contact too long")
	ErrSignatureTooLong = errors.New("signature too long")
)

// FeedbackSubmitInput 是反馈接口接受的全部字段。
//
// 隐私约定：这里刻意只保留四个字段，服务端不会读取或存储任何其他客户端字段
// （例如学号、账号、Cookie、设备信息），从结构上杜绝学生信息落库。
type FeedbackSubmitInput struct {
	Content string `json:"content"`
	Contact string `json:"contact"`
	// Signature 与 Anonymous 共同表达实名开关：
	// 前端在用户开启「实名发布」时置 Anonymous=false 并附带姓名；
	// 关闭或未填写姓名时一律按匿名处理。
	Signature string `json:"signature"`
	Anonymous bool   `json:"anonymous"`
}

// FeedbackService 意见反馈业务逻辑。
type FeedbackService struct {
	repo *repository.FeedbackRepository
}

// NewFeedbackService 构造反馈服务。
func NewFeedbackService(repo *repository.FeedbackRepository) *FeedbackService {
	return &FeedbackService{repo: repo}
}

// NormalizeInput 校验并规范化反馈输入（纯函数，便于单测）。
// 返回值中的 anonymous 由调用方按表单开关语义决定，这里只在无姓名时强制为匿名。
func NormalizeInput(input FeedbackSubmitInput) (*model.Feedback, error) {
	content := sanitizeText(input.Content)
	length := utf8.RuneCountInString(content)
	if length < MinContentLength {
		return nil, ErrContentTooShort
	}
	if length > MaxContentLength {
		return nil, ErrContentTooLong
	}

	contact := sanitizeText(input.Contact)
	if utf8.RuneCountInString(contact) > MaxContactLength {
		return nil, ErrContactTooLong
	}

	// 姓名仅在用户主动开启「实名发布」时才会随请求到达，
	// 此处仅作为本条反馈的附属文本保存，不与任何账号产生关联。
	signature := sanitizeText(input.Signature)
	if utf8.RuneCountInString(signature) > MaxSignatureLength {
		return nil, ErrSignatureTooLong
	}

	return &model.Feedback{
		Content:   content,
		Contact:   contact,
		Signature: signature,
		Anonymous: input.Anonymous || signature == "",
	}, nil
}

// Submit 校验并写入一条反馈。
func (s *FeedbackService) Submit(ctx context.Context, input FeedbackSubmitInput) (*model.Feedback, error) {
	feedback, err := NormalizeInput(input)
	if err != nil {
		return nil, err
	}
	if err := s.repo.Create(ctx, feedback); err != nil {
		return nil, err
	}
	return feedback, nil
}

// List 管理端分页查询。
func (s *FeedbackService) List(ctx context.Context, filter repository.FeedbackFilter) ([]model.Feedback, int64, error) {
	if filter.ReadState != repository.ReadStateRead && filter.ReadState != repository.ReadStateUnread {
		filter.ReadState = repository.ReadStateAll
	}
	filter.Keyword = strings.TrimSpace(filter.Keyword)
	return s.repo.List(ctx, filter)
}

// Get 查询单条反馈详情。
func (s *FeedbackService) Get(ctx context.Context, id string) (*model.Feedback, error) {
	return s.repo.GetByID(ctx, id)
}

// SetRead 标记已读 / 未读。
func (s *FeedbackService) SetRead(ctx context.Context, id string, isRead bool) (*model.Feedback, error) {
	return s.repo.SetRead(ctx, id, isRead)
}

// Delete 永久删除（调用方必须已校验超级管理员身份）。
func (s *FeedbackService) Delete(ctx context.Context, id string) error {
	return s.repo.Delete(ctx, id)
}

// UnreadCount 未读数量。
func (s *FeedbackService) UnreadCount(ctx context.Context) (int64, error) {
	return s.repo.UnreadCount(ctx)
}

// sanitizeText 去除首尾空白并剔除控制字符（保留换行与制表符），
// 避免不可见字符绕过长度校验或污染管理端展示。
func sanitizeText(raw string) string {
	trimmed := strings.TrimSpace(raw)
	var builder strings.Builder
	builder.Grow(len(trimmed))
	for _, r := range trimmed {
		if r == '\n' || r == '\t' {
			builder.WriteRune(r)
			continue
		}
		if unicode.IsControl(r) {
			continue
		}
		builder.WriteRune(r)
	}
	return strings.TrimSpace(builder.String())
}
