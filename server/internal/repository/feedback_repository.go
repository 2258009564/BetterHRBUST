// Package repository 提供数据访问层，仅操作反馈与管理员两个集合。
package repository

import (
	"context"
	"errors"
	"regexp"
	"time"

	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/bson/primitive"
	"go.mongodb.org/mongo-driver/mongo"
	"go.mongodb.org/mongo-driver/mongo/options"

	"github.com/Glassous/BetterHRBUST/server/internal/model"
)

// ErrNotFound 表示目标文档不存在。
var ErrNotFound = errors.New("document not found")

// 已读状态筛选值。
const (
	ReadStateAll    = "all"
	ReadStateRead   = "read"
	ReadStateUnread = "unread"
)

// FeedbackFilter 反馈列表查询条件。
type FeedbackFilter struct {
	Keyword   string
	ReadState string
	Limit     int64
	Offset    int64
}

// FeedbackRepository 反馈数据访问。
type FeedbackRepository struct {
	collection *mongo.Collection
}

// NewFeedbackRepository 构造反馈仓储。
func NewFeedbackRepository(db *mongo.Database) *FeedbackRepository {
	return &FeedbackRepository{collection: db.Collection(model.FeedbackCollection)}
}

// Create 写入一条反馈。
func (r *FeedbackRepository) Create(ctx context.Context, feedback *model.Feedback) error {
	if feedback.CreatedAt.IsZero() {
		feedback.CreatedAt = time.Now().UTC()
	}
	result, err := r.collection.InsertOne(ctx, feedback)
	if err != nil {
		return err
	}
	if oid, ok := result.InsertedID.(primitive.ObjectID); ok {
		feedback.ID = oid
	}
	return nil
}

// List 按创建时间倒序分页查询，并返回满足条件的总数。
func (r *FeedbackRepository) List(ctx context.Context, filter FeedbackFilter) ([]model.Feedback, int64, error) {
	query := bson.M{}
	switch filter.ReadState {
	case ReadStateRead:
		query["isRead"] = true
	case ReadStateUnread:
		query["isRead"] = false
	}
	if filter.Keyword != "" {
		pattern := primitive.Regex{Pattern: regexp.QuoteMeta(filter.Keyword), Options: "i"}
		query["$or"] = bson.A{
			bson.M{"content": pattern},
			bson.M{"contact": pattern},
			bson.M{"signature": pattern},
		}
	}

	total, err := r.collection.CountDocuments(ctx, query)
	if err != nil {
		return nil, 0, err
	}

	opts := options.Find().
		SetSort(bson.D{{Key: "createdAt", Value: -1}}).
		SetLimit(filter.Limit).
		SetSkip(filter.Offset)

	cursor, err := r.collection.Find(ctx, query, opts)
	if err != nil {
		return nil, 0, err
	}
	defer cursor.Close(ctx)

	items := make([]model.Feedback, 0, filter.Limit)
	if err := cursor.All(ctx, &items); err != nil {
		return nil, 0, err
	}
	return items, total, nil
}

// GetByID 按 ID 查询单条反馈。
func (r *FeedbackRepository) GetByID(ctx context.Context, id string) (*model.Feedback, error) {
	oid, err := primitive.ObjectIDFromHex(id)
	if err != nil {
		return nil, ErrNotFound
	}
	var feedback model.Feedback
	err = r.collection.FindOne(ctx, bson.M{"_id": oid}).Decode(&feedback)
	if errors.Is(err, mongo.ErrNoDocuments) {
		return nil, ErrNotFound
	}
	if err != nil {
		return nil, err
	}
	return &feedback, nil
}

// SetRead 更新已读状态。
func (r *FeedbackRepository) SetRead(ctx context.Context, id string, isRead bool) (*model.Feedback, error) {
	oid, err := primitive.ObjectIDFromHex(id)
	if err != nil {
		return nil, ErrNotFound
	}
	update := bson.M{"isRead": isRead, "readAt": nil}
	if isRead {
		now := time.Now().UTC()
		update["readAt"] = now
	}
	result, err := r.collection.UpdateOne(ctx, bson.M{"_id": oid}, bson.M{"$set": update})
	if err != nil {
		return nil, err
	}
	if result.MatchedCount == 0 {
		return nil, ErrNotFound
	}
	return r.GetByID(ctx, id)
}

// Delete 永久删除一条反馈（仅超级管理员可调用）。
func (r *FeedbackRepository) Delete(ctx context.Context, id string) error {
	oid, err := primitive.ObjectIDFromHex(id)
	if err != nil {
		return ErrNotFound
	}
	result, err := r.collection.DeleteOne(ctx, bson.M{"_id": oid})
	if err != nil {
		return err
	}
	if result.DeletedCount == 0 {
		return ErrNotFound
	}
	return nil
}

// UnreadCount 未读数量，用于管理端角标。
func (r *FeedbackRepository) UnreadCount(ctx context.Context) (int64, error) {
	return r.collection.CountDocuments(ctx, bson.M{"isRead": false})
}
