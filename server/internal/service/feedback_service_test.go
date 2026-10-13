package service

import (
	"errors"
	"strings"
	"testing"
)

func TestNormalizeInputTrimsAndKeepsAnonymous(t *testing.T) {
	feedback, err := NormalizeInput(FeedbackSubmitInput{
		Content: "  课程表在周次切换后偶尔会闪一下  ",
		Contact: "  student@example.com  ",
	})
	if err != nil {
		t.Fatalf("期望校验通过，实际错误: %v", err)
	}
	if feedback.Content != "课程表在周次切换后偶尔会闪一下" {
		t.Fatalf("正文未被去除首尾空白: %q", feedback.Content)
	}
	if feedback.Contact != "student@example.com" {
		t.Fatalf("联系方式未被去除首尾空白: %q", feedback.Contact)
	}
	if !feedback.Anonymous {
		t.Fatal("未开启实名时应标记为匿名")
	}
	if feedback.Signature != "" {
		t.Fatalf("匿名提交不应保存姓名: %q", feedback.Signature)
	}
}

func TestNormalizeInputRealName(t *testing.T) {
	feedback, err := NormalizeInput(FeedbackSubmitInput{
		Content:   "建议给成绩页增加导出 Excel 的入口",
		Signature: " 张三 ",
		Anonymous: false,
	})
	if err != nil {
		t.Fatalf("期望校验通过，实际错误: %v", err)
	}
	if feedback.Signature != "张三" {
		t.Fatalf("姓名未被规范化: %q", feedback.Signature)
	}
	if feedback.Anonymous {
		t.Fatal("开启实名后不应标记为匿名")
	}
}

// 即使客户端只发送姓名而未声明实名，也不能出现「有姓名却标记匿名」的矛盾状态。
func TestNormalizeInputSignatureWithoutFlagFallsBackToAnonymous(t *testing.T) {
	feedback, err := NormalizeInput(FeedbackSubmitInput{Content: "反馈内容测试文本", Anonymous: true})
	if err != nil {
		t.Fatalf("期望校验通过，实际错误: %v", err)
	}
	if !feedback.Anonymous {
		t.Fatal("无姓名时必须是匿名")
	}
}

func TestNormalizeInputRejectsTooShort(t *testing.T) {
	_, err := NormalizeInput(FeedbackSubmitInput{Content: " 短 "})
	if !errors.Is(err, ErrContentTooShort) {
		t.Fatalf("期望 ErrContentTooShort，实际: %v", err)
	}
}

func TestNormalizeInputRejectsTooLong(t *testing.T) {
	_, err := NormalizeInput(FeedbackSubmitInput{Content: strings.Repeat("哈", MaxContentLength+1)})
	if !errors.Is(err, ErrContentTooLong) {
		t.Fatalf("期望 ErrContentTooLong，实际: %v", err)
	}
}

func TestNormalizeInputRejectsLongContact(t *testing.T) {
	_, err := NormalizeInput(FeedbackSubmitInput{
		Content: "反馈正文内容合规",
		Contact: strings.Repeat("a", MaxContactLength+1),
	})
	if !errors.Is(err, ErrContactTooLong) {
		t.Fatalf("期望 ErrContactTooLong，实际: %v", err)
	}
}

func TestNormalizeInputRejectsLongSignature(t *testing.T) {
	_, err := NormalizeInput(FeedbackSubmitInput{
		Content:   "反馈正文内容合规",
		Signature: strings.Repeat("名", MaxSignatureLength+1),
	})
	if !errors.Is(err, ErrSignatureTooLong) {
		t.Fatalf("期望 ErrSignatureTooLong，实际: %v", err)
	}
}

// 控制字符必须被剔除，防止不可见字符绕过长度校验或污染管理端展示。
func TestSanitizeTextStripsControlCharacters(t *testing.T) {
	got := sanitizeText("正常\x00文本\x07带\r\n换行")
	if strings.ContainsAny(got, "\x00\x07\r") {
		t.Fatalf("控制字符未被剔除: %q", got)
	}
	if !strings.Contains(got, "\n") {
		t.Fatalf("换行符应被保留: %q", got)
	}
}
