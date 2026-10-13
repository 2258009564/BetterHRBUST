package repository

import (
	"context"
	"errors"
	"time"

	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/bson/primitive"
	"go.mongodb.org/mongo-driver/mongo"
	"go.mongodb.org/mongo-driver/mongo/options"

	"github.com/Glassous/BetterHRBUST/server/internal/model"
)

// ErrUsernameTaken 表示用户名已被占用。
var ErrUsernameTaken = errors.New("username taken")

// AdminRepository 管理员数据访问。
type AdminRepository struct {
	collection *mongo.Collection
}

// NewAdminRepository 构造管理员仓储。
func NewAdminRepository(db *mongo.Database) *AdminRepository {
	return &AdminRepository{collection: db.Collection(model.AdminCollection)}
}

// FindByUsername 按用户名查询管理员。
func (r *AdminRepository) FindByUsername(ctx context.Context, username string) (*model.Admin, error) {
	var admin model.Admin
	err := r.collection.FindOne(ctx, bson.M{"username": username}).Decode(&admin)
	if errors.Is(err, mongo.ErrNoDocuments) {
		return nil, ErrNotFound
	}
	if err != nil {
		return nil, err
	}
	return &admin, nil
}

// FindByID 按 ID 查询管理员。
func (r *AdminRepository) FindByID(ctx context.Context, id string) (*model.Admin, error) {
	oid, err := primitive.ObjectIDFromHex(id)
	if err != nil {
		return nil, ErrNotFound
	}
	var admin model.Admin
	err = r.collection.FindOne(ctx, bson.M{"_id": oid}).Decode(&admin)
	if errors.Is(err, mongo.ErrNoDocuments) {
		return nil, ErrNotFound
	}
	if err != nil {
		return nil, err
	}
	return &admin, nil
}

// Create 新建管理员；用户名重复时返回 ErrUsernameTaken。
func (r *AdminRepository) Create(ctx context.Context, admin *model.Admin) error {
	if admin.CreatedAt.IsZero() {
		admin.CreatedAt = time.Now().UTC()
	}
	if admin.Status == "" {
		admin.Status = model.StatusActive
	}
	result, err := r.collection.InsertOne(ctx, admin)
	if err != nil {
		if mongo.IsDuplicateKeyError(err) {
			return ErrUsernameTaken
		}
		return err
	}
	if oid, ok := result.InsertedID.(primitive.ObjectID); ok {
		admin.ID = oid
	}
	return nil
}

// List 返回全部管理员，创建时间正序（初始超管在最前）。
func (r *AdminRepository) List(ctx context.Context) ([]model.Admin, error) {
	opts := options.Find().SetSort(bson.D{{Key: "createdAt", Value: 1}})
	cursor, err := r.collection.Find(ctx, bson.M{}, opts)
	if err != nil {
		return nil, err
	}
	defer cursor.Close(ctx)

	items := make([]model.Admin, 0, 8)
	if err := cursor.All(ctx, &items); err != nil {
		return nil, err
	}
	return items, nil
}

// UpdateStatus 切换启用 / 暂停状态，并递增令牌版本使旧令牌失效。
func (r *AdminRepository) UpdateStatus(ctx context.Context, id, status string) (*model.Admin, error) {
	oid, err := primitive.ObjectIDFromHex(id)
	if err != nil {
		return nil, ErrNotFound
	}
	result, err := r.collection.UpdateOne(ctx,
		bson.M{"_id": oid},
		bson.M{
			"$set": bson.M{"status": status},
			"$inc": bson.M{"tokenVersion": 1},
		},
	)
	if err != nil {
		return nil, err
	}
	if result.MatchedCount == 0 {
		return nil, ErrNotFound
	}
	return r.FindByID(ctx, id)
}

// UpdatePassword 更新口令哈希、清除强制改密标记并递增令牌版本。
func (r *AdminRepository) UpdatePassword(ctx context.Context, id, passwordHash string, mustChange bool) error {
	oid, err := primitive.ObjectIDFromHex(id)
	if err != nil {
		return ErrNotFound
	}
	now := time.Now().UTC()
	result, err := r.collection.UpdateOne(ctx,
		bson.M{"_id": oid},
		bson.M{
			"$set": bson.M{
				"passwordHash":       passwordHash,
				"mustChangePassword": mustChange,
				"passwordUpdatedAt":  now,
			},
			"$inc": bson.M{"tokenVersion": 1},
		},
	)
	if err != nil {
		return err
	}
	if result.MatchedCount == 0 {
		return ErrNotFound
	}
	return nil
}

// CountByUsername 统计同名账号数量（供 seed 命令判断是否已存在）。
func (r *AdminRepository) CountByUsername(ctx context.Context, username string) (int64, error) {
	return r.collection.CountDocuments(ctx, bson.M{"username": username})
}
