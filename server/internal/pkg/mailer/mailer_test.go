package mailer

import (
	"encoding/base64"
	"mime"
	"strings"
	"testing"
)

func TestNewReturnsNilWhenNotConfigured(t *testing.T) {
	if New(Config{Port: 587, From: "team@example.com"}) != nil {
		t.Fatal("缺少 Host 时应返回 nil，表示邮件能力未启用")
	}
	if New(Config{Port: 587, Host: "smtp.example.com"}) != nil {
		t.Fatal("缺少 From 时应返回 nil，表示邮件能力未启用")
	}
}

func TestNewFillsDefaults(t *testing.T) {
	m := New(Config{Host: " smtp.example.com ", From: " team@example.com "})
	if m == nil {
		t.Fatal("配置完整时应构造成功")
	}
	if m.cfg.Port != 587 {
		t.Fatalf("端口缺省应为 587，实际: %d", m.cfg.Port)
	}
	if m.FromName() != DefaultFromName {
		t.Fatalf("显示名前缀应回退默认值，实际: %q", m.FromName())
	}
	if !m.Enabled() {
		t.Fatal("配置完整应由 Enabled 返回 true")
	}
	if (*Mailer)(nil).Enabled() {
		t.Fatal("nil 接收者应安全返回 false")
	}
}

func TestSenderDisplayNameAppendsOperator(t *testing.T) {
	m := New(Config{Host: "smtp.example.com", From: "team@example.com", FromName: "BetterHRBUST团队"})
	if got := m.senderDisplayName("审核员"); got != "BetterHRBUST团队·审核员" {
		t.Fatalf("显示名应为「前缀·昵称」，实际: %q", got)
	}
	if got := m.senderDisplayName("  "); got != "BetterHRBUST团队" {
		t.Fatalf("昵称为空时应只保留前缀，实际: %q", got)
	}
}

func TestBuildMessageEncodesHeadersAndBody(t *testing.T) {
	m := New(Config{Host: "smtp.example.com", From: "team@example.com", FromName: "BetterHRBUST团队"})
	raw := string(m.buildMessage("student@example.com", "关于成绩导出的回复", "您好，\n导出功能已排期。", "审核员"))

	// 中文标题与显示名必须做 RFC 2047 编码，否则邮件头会乱码。
	if !strings.Contains(raw, "Subject: "+mime.QEncoding.Encode("utf-8", "关于成绩导出的回复")) {
		t.Fatalf("标题未按 RFC 2047 编码:\n%s", raw)
	}
	if !strings.Contains(raw, "From: "+mime.QEncoding.Encode("utf-8", "BetterHRBUST团队·审核员")) {
		t.Fatalf("发件人显示名未按 RFC 2047 编码:\n%s", raw)
	}
	if !strings.Contains(raw, "<team@example.com>") {
		t.Fatalf("缺少发件地址:\n%s", raw)
	}
	if !strings.Contains(raw, "To: <student@example.com>") {
		t.Fatalf("缺少收件地址:\n%s", raw)
	}
	if !strings.Contains(raw, "Content-Type: text/plain; charset=UTF-8") {
		t.Fatalf("缺少正文内容类型声明:\n%s", raw)
	}

	// 正文以 base64 承载，解析后应与原文一致（含换行）。
	encoded := raw[strings.Index(raw, "\r\n\r\n")+4:]
	encoded = strings.TrimRight(encoded, "\r\n")
	decoded, err := base64.StdEncoding.DecodeString(encoded)
	if err != nil {
		t.Fatalf("正文不是合法 base64: %v", err)
	}
	if string(decoded) != "您好，\n导出功能已排期。" {
		t.Fatalf("正文解码结果不一致: %q", string(decoded))
	}
}

// 标题中的换行必须被清理，防止凭空多出邮件头。
func TestBuildMessageStripsHeaderInjection(t *testing.T) {
	m := New(Config{Host: "smtp.example.com", From: "team@example.com"})
	raw := string(m.buildMessage("student@example.com", "标题\r\nBcc: evil@example.com", "正文", ""))

	for _, line := range strings.Split(raw, "\r\n") {
		if strings.HasPrefix(strings.ToLower(line), "bcc:") {
			t.Fatalf("标题中的换行应被清理，实际注入了独立邮件头:\n%s", raw)
		}
	}
	if !strings.Contains(raw, "Subject: ") {
		t.Fatalf("仍需保留标题头:\n%s", raw)
	}
}
