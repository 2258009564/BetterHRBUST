// Package jwtutil 封装管理端登录令牌的签发与解析。
package jwtutil

import (
	"errors"
	"time"

	"github.com/golang-jwt/jwt/v5"

	"github.com/Glassous/BetterHRBUST/server/internal/model"
)

// ErrInvalidToken 表示令牌缺失、过期、签名不匹配或版本失效。
var ErrInvalidToken = errors.New("invalid token")

// Claims 管理端令牌载荷：仅包含账号标识与角色，不含任何学生信息。
type Claims struct {
	Username     string `json:"username"`
	Role         string `json:"role"`
	TokenVersion int64  `json:"tv"`
	jwt.RegisteredClaims
}

// Issue 为指定管理员签发令牌。
func Issue(secret string, ttl time.Duration, admin *model.Admin) (string, error) {
	now := time.Now()
	claims := Claims{
		Username:     admin.Username,
		Role:         admin.Role,
		TokenVersion: admin.TokenVersion,
		RegisteredClaims: jwt.RegisteredClaims{
			Subject:   admin.ID.Hex(),
			IssuedAt:  jwt.NewNumericDate(now),
			ExpiresAt: jwt.NewNumericDate(now.Add(ttl)),
			Issuer:    "betterhrbust-server",
		},
	}
	return jwt.NewWithClaims(jwt.SigningMethodHS256, claims).SignedString([]byte(secret))
}

// Parse 校验并解析令牌。
func Parse(secret, token string) (*Claims, error) {
	parsed, err := jwt.ParseWithClaims(token, &Claims{}, func(t *jwt.Token) (any, error) {
		if _, ok := t.Method.(*jwt.SigningMethodHMAC); !ok {
			return nil, ErrInvalidToken
		}
		return []byte(secret), nil
	}, jwt.WithValidMethods([]string{jwt.SigningMethodHS256.Alg()}))
	if err != nil {
		return nil, ErrInvalidToken
	}
	claims, ok := parsed.Claims.(*Claims)
	if !ok || !parsed.Valid || claims.Subject == "" {
		return nil, ErrInvalidToken
	}
	return claims, nil
}
