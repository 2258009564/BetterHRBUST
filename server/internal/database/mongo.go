// Package database 负责 MongoDB / Redis 的连接与集合初始化。
package database

import (
	"context"
	"time"

	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/mongo"
	"go.mongodb.org/mongo-driver/mongo/options"

	"github.com/Glassous/BetterHRBUST/server/internal/model"
)

// ConnectMongo 建立 MongoDB 连接并完成连通性探测。
func ConnectMongo(ctx context.Context, uri string) (*mongo.Client, error) {
	opts := options.Client().ApplyURI(uri).
		SetMaxPoolSize(20).
		SetMinPoolSize(2).
		SetConnectTimeout(8 * time.Second).
		SetServerSelectionTimeout(8 * time.Second)

	client, err := mongo.Connect(ctx, opts)
	if err != nil {
		return nil, err
	}
	pingCtx, cancel := context.WithTimeout(ctx, 8*time.Second)
	defer cancel()
	if err := client.Ping(pingCtx, nil); err != nil {
		_ = client.Disconnect(context.Background())
		return nil, err
	}
	return client, nil
}

// EnsureIndexes 建立必要索引：
//   - feedback.createdAt 倒序：列表默认排序；
//   - feedback.isRead：已读状态筛选；
//   - admins.username 唯一：避免重名账号。
func EnsureIndexes(ctx context.Context, db *mongo.Database) error {
	feedback := db.Collection(model.FeedbackCollection)
	if _, err := feedback.Indexes().CreateMany(ctx, []mongo.IndexModel{
		{Keys: bson.D{{Key: "createdAt", Value: -1}}},
		{Keys: bson.D{{Key: "isRead", Value: 1}}},
	}); err != nil {
		return err
	}

	admins := db.Collection(model.AdminCollection)
	_, err := admins.Indexes().CreateOne(ctx, mongo.IndexModel{
		Keys:    bson.D{{Key: "username", Value: 1}},
		Options: options.Index().SetUnique(true),
	})
	return err
}
