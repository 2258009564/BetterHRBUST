// Package model 定义持久化文档结构。
//
// ⚠️ 隐私红线：本文件中不存在也不允许新增任何学生身份字段
// （学号、教务内部 ID、姓名绑定的账号、Cookie、成绩、课表等）。
// 反馈文档只承载用户主动填写的反馈文本，姓名仅作为该条反馈的一部分。
package model

import (
	"time"

	"go.mongodb.org/mongo-driver/bson/primitive"
)

// FeedbackCollection 是反馈集合名。
const FeedbackCollection = "feedback"

// Feedback 一条意见反馈。
type Feedback struct {
	ID      primitive.ObjectID `bson:"_id,omitempty" json:"id"`
	Content string             `bson:"content" json:"content"`
	// Contact 用户自愿填写的联系方式（可空）。
	Contact string `bson:"contact,omitempty" json:"contact,omitempty"`
	// Signature 实名发布时由前端读取并提交的姓名文本，仅作为本条反馈的一部分存放，
	// 不建立任何账号关联、不单独建集合、不参与身份匹配。
	Signature string `bson:"signature,omitempty" json:"signature,omitempty"`
	// Anonymous 提交时是否处于匿名状态（用于管理端展示）。
	Anonymous bool       `bson:"anonymous" json:"anonymous"`
	IsRead    bool       `bson:"isRead" json:"isRead"`
	ReadAt    *time.Time `bson:"readAt,omitempty" json:"readAt,omitempty"`
	CreatedAt time.Time  `bson:"createdAt" json:"createdAt"`
}
