// Package randpass 生成一次性初始口令。
//
// 使用 crypto/rand，字符集剔除易混淆字符（0/O、1/l/I），
// 并保证字母与数字同时出现，便于人工抄写后立即登录改密。
package randpass

import (
	"crypto/rand"
	"math/big"
)

const (
	upper  = "ABCDEFGHJKLMNPQRSTUVWXYZ"
	lower  = "abcdefghijkmnpqrstuvwxyz"
	digits = "23456789"
	all    = upper + lower + digits
)

// Generate 生成指定长度的随机口令；长度下限为 10。
func Generate(length int) string {
	if length < 10 {
		length = 10
	}
	out := make([]byte, 0, length)
	out = append(out, pick(upper), pick(lower), pick(digits))
	for len(out) < length {
		out = append(out, pick(all))
	}
	shuffle(out)
	return string(out)
}

func pick(set string) byte {
	n, err := rand.Int(rand.Reader, big.NewInt(int64(len(set))))
	if err != nil {
		return set[0]
	}
	return set[n.Int64()]
}

func shuffle(buf []byte) {
	for i := len(buf) - 1; i > 0; i-- {
		n, err := rand.Int(rand.Reader, big.NewInt(int64(i+1)))
		if err != nil {
			return
		}
		j := n.Int64()
		buf[i], buf[j] = buf[j], buf[i]
	}
}
