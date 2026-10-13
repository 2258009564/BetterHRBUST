package randpass

import (
	"strings"
	"testing"
	"unicode"
)

func TestGenerateLengthAndCharset(t *testing.T) {
	for i := 0; i < 50; i++ {
		generated := Generate(14)
		if len([]rune(generated)) != 14 {
			t.Fatalf("长度不符合预期: %q", generated)
		}
		if strings.ContainsAny(generated, "0O1lI") {
			t.Fatalf("包含易混淆字符: %q", generated)
		}
		var hasUpper, hasLower, hasDigit bool
		for _, r := range generated {
			switch {
			case unicode.IsUpper(r):
				hasUpper = true
			case unicode.IsLower(r):
				hasLower = true
			case unicode.IsDigit(r):
				hasDigit = true
			default:
				t.Fatalf("出现非法字符 %q: %q", r, generated)
			}
		}
		if !hasUpper || !hasLower || !hasDigit {
			t.Fatalf("字母与数字未同时出现: %q", generated)
		}
	}
}

func TestGenerateEnforcesMinimumLength(t *testing.T) {
	if got := Generate(4); len([]rune(got)) < 10 {
		t.Fatalf("长度下限未生效: %q", got)
	}
}
