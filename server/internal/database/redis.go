package database

import (
	"context"
	"time"

	"github.com/redis/go-redis/v9"
)

// ConnectRedis 建立 Redis 连接（用于限流计数，不承载任何业务数据）。
// addr 为空时返回 nil，表示限流功能降级关闭。
func ConnectRedis(ctx context.Context, addr, password string, dbIndex int) (*redis.Client, error) {
	if addr == "" {
		return nil, nil
	}
	client := redis.NewClient(&redis.Options{
		Addr:         addr,
		Password:     password,
		DB:           dbIndex,
		DialTimeout:  5 * time.Second,
		ReadTimeout:  3 * time.Second,
		WriteTimeout: 3 * time.Second,
		PoolSize:     10,
	})
	pingCtx, cancel := context.WithTimeout(ctx, 5*time.Second)
	defer cancel()
	if err := client.Ping(pingCtx).Err(); err != nil {
		_ = client.Close()
		return nil, err
	}
	return client, nil
}
