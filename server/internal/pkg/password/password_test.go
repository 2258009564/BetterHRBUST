package password

import "testing"

func TestHashAndVerify(t *testing.T) {
	hash, err := Hash("Hrbust-2026-feedback")
	if err != nil {
		t.Fatalf("生成哈希失败: %v", err)
	}
	if hash == "Hrbust-2026-feedback" {
		t.Fatal("哈希结果不应当等于明文")
	}
	if !Verify(hash, "Hrbust-2026-feedback") {
		t.Fatal("正确口令校验失败")
	}
	if Verify(hash, "Hrbust-2026-feedback-x") {
		t.Fatal("错误口令通过了校验")
	}
}

func TestIsAcceptable(t *testing.T) {
	if IsAcceptable("1234567") {
		t.Fatal("7 位口令不应通过")
	}
	if !IsAcceptable("12345678") {
		t.Fatal("8 位口令应通过")
	}
}
