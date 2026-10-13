package middleware

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"log"
	"net/http"
	"strconv"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/redis/go-redis/v9"

	"github.com/Glassous/BetterHRBUST/server/internal/pkg/response"
)

// RateLimitRule 一条固定窗口限流规则。
type RateLimitRule struct {
	Limit  int64
	Window time.Duration
}

// RateLimiter 基于 Redis 的固定窗口限流；Redis 不可用时降级放行（fail-open），
// 保证反馈提交不会因缓存故障而完全不可用。
type RateLimiter struct {
	client *redis.Client
	salt   string
	rules  []RateLimitRule
}

// NewRateLimiter 创建限流器；client 为 nil 时所有调用直接放行。
func NewRateLimiter(client *redis.Client, salt string, rules ...RateLimitRule) *RateLimiter {
	return &RateLimiter{client: client, salt: salt, rules: rules}
}

// Enabled 表示限流是否真正生效。
func (r *RateLimiter) Enabled() bool {
	return r != nil && r.client != nil && len(r.rules) > 0
}

// Middleware 返回 Gin 中间件；返回 429 时使用统一业务码 1002。
func (r *RateLimiter) Middleware(scope string, extraKey func(*gin.Context) string) gin.HandlerFunc {
	return func(c *gin.Context) {
		if !r.Enabled() {
			c.Next()
			return
		}
		identity := r.hash(c.ClientIP())
		if extraKey != nil {
			identity += ":" + extraKey(c)
		}
		for index, rule := range r.rules {
			key := "rl:" + scope + ":" + strconv.Itoa(index) + ":" + identity
			allowed, err := r.allow(c.Request.Context(), key, rule)
			if err != nil {
				log.Printf("[ratelimit] Redis 异常，本次请求放行: %v", err)
				c.Next()
				return
			}
			if !allowed {
				response.Fail(c, http.StatusTooManyRequests, response.CodeRateLimited, "提交过于频繁，请稍后再试")
				return
			}
		}
		c.Next()
	}
}

// allow 执行一次 INCR+EXPIRE（Pipeline 合并为单次往返）。
func (r *RateLimiter) allow(ctx context.Context, key string, rule RateLimitRule) (bool, error) {
	pipe := r.client.Pipeline()
	countCmd := pipe.Incr(ctx, key)
	pipe.Expire(ctx, key, rule.Window)
	if _, err := pipe.Exec(ctx); err != nil {
		return true, err
	}
	return countCmd.Val() <= rule.Limit, nil
}

// hash 对上送标识做单向散列：Redis 中只出现哈希值，绝不出现原始 IP。
func (r *RateLimiter) hash(value string) string {
	sum := sha256.Sum256([]byte(r.salt + "|" + value))
	return hex.EncodeToString(sum[:16])
}
