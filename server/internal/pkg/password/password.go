// Package password 封装管理员口令的哈希与校验（bcrypt）。
package password

import "golang.org/x/crypto/bcrypt"

const minLength = 8

// Hash 生成 bcrypt 哈希。
func Hash(plain string) (string, error) {
	buf, err := bcrypt.GenerateFromPassword([]byte(plain), bcrypt.DefaultCost)
	if err != nil {
		return "", err
	}
	return string(buf), nil
}

// Verify 校验明文口令与哈希是否匹配。
func Verify(hash, plain string) bool {
	return bcrypt.CompareHashAndPassword([]byte(hash), []byte(plain)) == nil
}

// IsAcceptable 判断口令长度是否达到最低要求（仅长度约束，不做复杂度强制）。
func IsAcceptable(plain string) bool {
	return len([]rune(plain)) >= minLength
}
