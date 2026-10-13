// Command seed-admin 通过命令行向数据库插入初始超级管理员。
//
// 用法（在 server/ 目录下执行）：
//
//	go run ./cmd/seed-admin -username admin -nickname 超级管理员
//	go run ./cmd/seed-admin -username admin -nickname 超级管理员 -password 你的自定义密码
//
// 未指定 -password 时会自动生成一个初始口令并打印到终端，请立即保存；
// 密码在数据库中仅以 bcrypt 哈希形式存在，无法再次取回。
package main

import (
	"context"
	"flag"
	"fmt"
	"log"
	"time"

	"github.com/Glassous/BetterHRBUST/server/internal/config"
	"github.com/Glassous/BetterHRBUST/server/internal/database"
	"github.com/Glassous/BetterHRBUST/server/internal/repository"
	"github.com/Glassous/BetterHRBUST/server/internal/service"
)

func main() {
	username := flag.String("username", "admin", "超级管理员用户名（3-32 位字母、数字、下划线、点或短横线）")
	nickname := flag.String("nickname", "超级管理员", "超级管理员昵称")
	plainPassword := flag.String("password", "", "初始密码；留空则自动生成（至少 8 位）")
	flag.Parse()

	cfg := config.Load()

	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
	defer cancel()

	client, err := database.ConnectMongo(ctx, cfg.MongoURI)
	if err != nil {
		log.Fatalf("连接 MongoDB 失败: %v", err)
	}
	defer func() {
		closeCtx, closeCancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer closeCancel()
		_ = client.Disconnect(closeCtx)
	}()

	db := client.Database(cfg.MongoDB)
	if err := database.EnsureIndexes(ctx, db); err != nil {
		log.Fatalf("初始化集合索引失败: %v", err)
	}

	adminService := service.NewAdminService(repository.NewAdminRepository(db), cfg.JWTSecret, cfg.JWTTTL)
	result, created, err := adminService.SeedSuperAdmin(ctx, *username, *nickname, *plainPassword)
	if err != nil {
		log.Fatalf("创建超级管理员失败: %v", err)
	}

	if !created {
		fmt.Printf("账号 %s 已存在，未做任何修改。\n", *username)
		fmt.Println("如需重置该账号口令，请登录管理端后在「账号安全」中修改，或先手动删除该账号再重新执行本命令。")
		return
	}

	fmt.Println("超级管理员创建成功：")
	fmt.Printf("  用户名：%s\n", result.Admin.Username)
	fmt.Printf("  昵称：%s\n", result.Admin.Nickname)
	if result.Generated {
		fmt.Printf("  初始密码（自动生成，仅显示这一次）：%s\n", result.GeneratedPassword)
	} else {
		fmt.Println("  初始密码：（使用 -password 指定的口令）")
	}
	fmt.Println("请立即登录管理端并妥善保管口令；数据库仅保存哈希，无法找回。")
}
