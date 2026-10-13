package model

import (
	"time"

	"go.mongodb.org/mongo-driver/bson/primitive"
)

// AdminCollection 是管理员集合名。
const AdminCollection = "admins"

// 管理员角色：super 为初始超级管理员，admin 为普通管理员（仅可阅读反馈）。
const (
	RoleSuper = "super"
	RoleAdmin = "admin"
)

// 管理员状态：suspended 表示资格被暂停，无法登录且已签发令牌立即失效。
const (
	StatusActive    = "active"
	StatusSuspended = "suspended"
)

// Admin 管理端账号。
type Admin struct {
	ID           primitive.ObjectID `bson:"_id,omitempty" json:"id"`
	Username     string             `bson:"username" json:"username"`
	Nickname     string             `bson:"nickname" json:"nickname"`
	PasswordHash string             `bson:"passwordHash" json:"-"`
	Role         string             `bson:"role" json:"role"`
	Status       string             `bson:"status" json:"status"`
	// MustChangePassword 首次登录（或超管重置口令后）必须改密才能进入业务页面。
	MustChangePassword bool `bson:"mustChangePassword" json:"mustChangePassword"`
	// TokenVersion 递增版本号：改密 / 暂停 / 恢复时自增，使已签发令牌立即失效。
	TokenVersion      int64      `bson:"tokenVersion" json:"-"`
	CreatedAt         time.Time  `bson:"createdAt" json:"createdAt"`
	CreatedBy         string     `bson:"createdBy,omitempty" json:"createdBy,omitempty"`
	PasswordUpdatedAt *time.Time `bson:"passwordUpdatedAt,omitempty" json:"passwordUpdatedAt,omitempty"`
}
