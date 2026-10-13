// Package mailer 提供基于标准库的最小 SMTP 发信能力。
//
// 刻意不引入第三方邮件库：本项目只需要「纯文本正文 + 中文标题 / 中文显示名」这一种邮件形态，
// net/smtp 搭配 mime 足以覆盖，同时避免额外依赖带来的供应链风险。
//
// 隐私约定：本包只负责把管理员撰写的回复文本投递到收件邮箱，
// 不记录、不缓存任何收件人信息与邮件正文。
package mailer

import (
	"bytes"
	"context"
	"crypto/tls"
	"encoding/base64"
	"errors"
	"fmt"
	"mime"
	"net"
	"net/smtp"
	"strconv"
	"strings"
	"time"
)

// ErrNotConfigured 表示 SMTP 未完整配置，发信能力不可用。
var ErrNotConfigured = errors.New("smtp not configured")

const (
	// defaultTimeout 单次发信的端到端超时，避免慢 SMTP 拖垮管理端请求。
	defaultTimeout = 15 * time.Second
	// DefaultFromName 发件人显示名前缀的兜底值（可用 SMTP_FROM_NAME 覆盖）。
	DefaultFromName = "BetterHRBUST"
	// nameSeparator 显示名前缀与管理员昵称之间的分隔符。
	nameSeparator = "·"
	// base64LineLength base64 正文按 76 列折行（RFC 2045）。
	base64LineLength = 76
)

// Config 汇总 SMTP 连接与发件人参数。
type Config struct {
	Host     string
	Port     int
	Username string
	Password string
	// From 发件邮箱地址，必须是 SMTP 服务允许的发件人。
	From string
	// FromName 显示名前缀，最终显示为「FromName·管理员昵称」。
	FromName string
	// UseTLS 为 true 时使用隐式 TLS（典型场景：465 端口）；
	// 为 false 时先明文连接，服务端声明 STARTTLS 时自动升级（典型场景：587 端口）。
	UseTLS bool
}

// Mailer 每次发送建立独立连接，可被多个 goroutine 并发使用。
type Mailer struct {
	cfg     Config
	timeout time.Duration
}

// New 构造发信器；Host 或 From 为空时返回 nil，表示邮件能力未启用。
func New(cfg Config) *Mailer {
	cfg.Host = strings.TrimSpace(cfg.Host)
	cfg.From = strings.TrimSpace(cfg.From)
	if cfg.Host == "" || cfg.From == "" {
		return nil
	}
	if cfg.Port <= 0 || cfg.Port > 65535 {
		cfg.Port = 587
	}
	cfg.Username = strings.TrimSpace(cfg.Username)
	cfg.FromName = strings.TrimSpace(cfg.FromName)
	if cfg.FromName == "" {
		cfg.FromName = DefaultFromName
	}
	return &Mailer{cfg: cfg, timeout: defaultTimeout}
}

// Enabled 判断发信器是否可用（nil 接收者安全）。
func (m *Mailer) Enabled() bool { return m != nil }

// FromName 返回配置的显示名前缀（nil 接收者返回空串）。
func (m *Mailer) FromName() string {
	if m == nil {
		return ""
	}
	return m.cfg.FromName
}

// Send 向 to 投递一封纯文本邮件。
// operator 为操作管理员昵称，会拼接进发件人显示名；为空时只保留配置的前缀。
func (m *Mailer) Send(ctx context.Context, to, subject, body, operator string) error {
	if m == nil {
		return ErrNotConfigured
	}
	to = strings.TrimSpace(to)
	if to == "" {
		return errors.New("收件邮箱为空")
	}

	deadline := time.Now().Add(m.timeout)
	if d, ok := ctx.Deadline(); ok && d.Before(deadline) {
		deadline = d
	}

	conn, err := m.dial(ctx, net.JoinHostPort(m.cfg.Host, strconv.Itoa(m.cfg.Port)))
	if err != nil {
		return fmt.Errorf("连接 SMTP 服务器失败: %w", err)
	}
	_ = conn.SetDeadline(deadline)
	defer func() { _ = conn.Close() }()

	client, err := smtp.NewClient(conn, m.cfg.Host)
	if err != nil {
		return fmt.Errorf("建立 SMTP 会话失败: %w", err)
	}
	defer func() { _ = client.Close() }()

	if !m.cfg.UseTLS {
		if supported, _ := client.Extension("STARTTLS"); supported {
			if err := client.StartTLS(m.tlsConfig()); err != nil {
				return fmt.Errorf("STARTTLS 握手失败: %w", err)
			}
		}
	}
	if m.cfg.Username != "" {
		if err := client.Auth(smtp.PlainAuth("", m.cfg.Username, m.cfg.Password, m.cfg.Host)); err != nil {
			return fmt.Errorf("SMTP 认证失败: %w", err)
		}
	}
	if err := client.Mail(m.cfg.From); err != nil {
		return fmt.Errorf("设置发件人失败: %w", err)
	}
	if err := client.Rcpt(to); err != nil {
		return fmt.Errorf("设置收件人失败: %w", err)
	}

	writer, err := client.Data()
	if err != nil {
		return fmt.Errorf("开始写入邮件内容失败: %w", err)
	}
	if _, err := writer.Write(m.buildMessage(to, subject, body, operator)); err != nil {
		_ = writer.Close()
		return fmt.Errorf("写入邮件内容失败: %w", err)
	}
	if err := writer.Close(); err != nil {
		return fmt.Errorf("提交邮件内容失败: %w", err)
	}
	if err := client.Quit(); err != nil {
		return fmt.Errorf("结束 SMTP 会话失败: %w", err)
	}
	return nil
}

// dial 建立到 SMTP 服务器的连接，UseTLS 时直接完成 TLS 握手。
func (m *Mailer) dial(ctx context.Context, addr string) (net.Conn, error) {
	dialer := &net.Dialer{Timeout: m.timeout}
	if !m.cfg.UseTLS {
		return dialer.DialContext(ctx, "tcp", addr)
	}
	tlsDialer := &tls.Dialer{NetDialer: dialer, Config: m.tlsConfig()}
	return tlsDialer.DialContext(ctx, "tcp", addr)
}

func (m *Mailer) tlsConfig() *tls.Config {
	return &tls.Config{ServerName: m.cfg.Host, MinVersion: tls.VersionTLS12}
}

// buildMessage 生成 RFC 5322 报文体：标题与显示名做 RFC 2047 编码，正文 base64 编码。
func (m *Mailer) buildMessage(to, subject, body, operator string) []byte {
	var buf bytes.Buffer
	fmt.Fprintf(&buf, "From: %s\r\n", formatAddress(m.senderDisplayName(operator), m.cfg.From))
	fmt.Fprintf(&buf, "To: <%s>\r\n", to)
	fmt.Fprintf(&buf, "Subject: %s\r\n", mime.QEncoding.Encode("utf-8", sanitizeHeader(subject)))
	fmt.Fprintf(&buf, "Date: %s\r\n", time.Now().Format(time.RFC1123Z))
	buf.WriteString("MIME-Version: 1.0\r\n")
	buf.WriteString("Content-Type: text/plain; charset=UTF-8\r\n")
	buf.WriteString("Content-Transfer-Encoding: base64\r\n")
	buf.WriteString("\r\n")
	buf.WriteString(encodeBase64Lines(body))
	buf.WriteString("\r\n")
	return buf.Bytes()
}

// senderDisplayName 拼接发件人显示名：前缀 + 分隔符 + 管理员昵称。
func (m *Mailer) senderDisplayName(operator string) string {
	name := strings.TrimSpace(operator)
	if name == "" {
		return m.cfg.FromName
	}
	return m.cfg.FromName + nameSeparator + name
}

// formatAddress 生成 "显示名 <地址>" 形式；中文显示名由 mime.QEncoding 做 RFC 2047 编码。
func formatAddress(name, addr string) string {
	trimmed := strings.TrimSpace(name)
	if trimmed == "" {
		return "<" + addr + ">"
	}
	return mime.QEncoding.Encode("utf-8", trimmed) + " <" + addr + ">"
}

// sanitizeHeader 去除邮件头中的换行，防止标题注入额外头字段。
func sanitizeHeader(value string) string {
	replaced := strings.NewReplacer("\r", " ", "\n", " ", "\t", " ").Replace(value)
	return strings.TrimSpace(replaced)
}

// encodeBase64Lines 将正文按 base64 编码并折行，兼容对行长敏感的中转服务器。
func encodeBase64Lines(text string) string {
	encoded := base64.StdEncoding.EncodeToString([]byte(text))
	var builder strings.Builder
	builder.Grow(len(encoded) + len(encoded)/base64LineLength*2)
	for len(encoded) > base64LineLength {
		builder.WriteString(encoded[:base64LineLength])
		builder.WriteString("\r\n")
		encoded = encoded[base64LineLength:]
	}
	builder.WriteString(encoded)
	return builder.String()
}
