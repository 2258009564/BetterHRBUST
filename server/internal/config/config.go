// Package config 负责从环境变量（可选叠加 server/.env）加载服务端配置。
//
// 隐私约定：配置中不包含任何学生信息相关字段，服务端也不接受此类输入。
package config

import (
	"bufio"
	"crypto/rand"
	"encoding/hex"
	"log"
	"os"
	"strconv"
	"strings"
	"time"
)

const (
	defaultPort       = "9191"
	defaultMongoURI   = "mongodb://127.0.0.1:27017"
	defaultMongoDB    = "betterhrbust"
	defaultJWTTTLHour = 12
	defaultMaxPage    = 100
)

// Config 汇总服务端运行所需配置。
type Config struct {
	Port          string
	MongoURI      string
	MongoDB       string
	RedisAddr     string
	RedisPassword string
	RedisDB       int
	JWTSecret     string
	JWTTTL        time.Duration
	CORSOrigins   []string
	// TrustedProxies 为空表示不信任任何代理头，直接使用连接来源地址；
	// 容器部署时由反向代理转发，需要显式声明代理网段。
	TrustedProxies []string
	RateLimit      bool
	MaxPageSize    int64
	// Warnings 收集启动期的降级提示，由调用方决定是否输出（命令行工具可静默忽略）。
	Warnings []string
}

// Load 读取环境变量并返回配置；缺失项使用安全默认值。
func Load() Config {
	loadEnvFile(".env")
	loadEnvFile("server/.env")

	cfg := Config{
		Port:           envString("PORT", defaultPort),
		MongoURI:       envString("MONGO_URI", defaultMongoURI),
		MongoDB:        envString("MONGO_DB", defaultMongoDB),
		RedisAddr:      envString("REDIS_ADDR", ""),
		RedisPassword:  envString("REDIS_PASSWORD", ""),
		RedisDB:        int(envInt("REDIS_DB", 0)),
		JWTSecret:      envString("JWT_SECRET", ""),
		JWTTTL:         time.Duration(envInt("JWT_TTL_HOURS", defaultJWTTTLHour)) * time.Hour,
		CORSOrigins:    envList("CORS_ORIGINS", []string{"http://localhost:5173", "http://localhost:5174", "http://127.0.0.1:5173", "http://127.0.0.1:5174"}),
		TrustedProxies: envList("TRUSTED_PROXIES", nil),
		RateLimit:      envBool("RATE_LIMIT_ENABLED", true),
		MaxPageSize:    envInt("MAX_PAGE_SIZE", defaultMaxPage),
	}

	if cfg.JWTSecret == "" {
		cfg.JWTSecret = randomSecret()
		cfg.Warnings = append(cfg.Warnings, "未提供 JWT_SECRET，已生成临时密钥（重启后旧登录令牌失效）；生产环境请显式配置")
	}
	if cfg.RedisAddr == "" {
		cfg.RateLimit = false
		cfg.Warnings = append(cfg.Warnings, "未配置 REDIS_ADDR，限流已自动降级为关闭状态")
	}
	if cfg.MaxPageSize < 1 || cfg.MaxPageSize > 500 {
		cfg.MaxPageSize = defaultMaxPage
	}
	return cfg
}

// loadEnvFile 读取 KEY=VALUE 形式的配置文件；已存在的环境变量优先，不被覆盖。
func loadEnvFile(path string) {
	file, err := os.Open(path)
	if err != nil {
		return
	}
	defer file.Close()

	scanner := bufio.NewScanner(file)
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		key, value, found := strings.Cut(line, "=")
		if !found {
			continue
		}
		key = strings.TrimSpace(key)
		value = strings.Trim(strings.TrimSpace(value), `"'`)
		if key == "" {
			continue
		}
		if _, exists := os.LookupEnv(key); !exists {
			_ = os.Setenv(key, value)
		}
	}
}

func envString(key, fallback string) string {
	if v := strings.TrimSpace(os.Getenv(key)); v != "" {
		return v
	}
	return fallback
}

func envInt(key string, fallback int64) int64 {
	v := strings.TrimSpace(os.Getenv(key))
	if v == "" {
		return fallback
	}
	n, err := strconv.ParseInt(v, 10, 64)
	if err != nil {
		log.Printf("[config] %s 取值非法，回退默认值 %d", key, fallback)
		return fallback
	}
	return n
}

func envBool(key string, fallback bool) bool {
	v := strings.TrimSpace(os.Getenv(key))
	if v == "" {
		return fallback
	}
	b, err := strconv.ParseBool(v)
	if err != nil {
		return fallback
	}
	return b
}

func envList(key string, fallback []string) []string {
	v := strings.TrimSpace(os.Getenv(key))
	if v == "" {
		return fallback
	}
	if v == "*" {
		return []string{"*"}
	}
	parts := strings.Split(v, ",")
	out := make([]string, 0, len(parts))
	for _, p := range parts {
		if trimmed := strings.TrimSpace(p); trimmed != "" {
			out = append(out, trimmed)
		}
	}
	if len(out) == 0 {
		return fallback
	}
	return out
}

func randomSecret() string {
	buf := make([]byte, 32)
	if _, err := rand.Read(buf); err != nil {
		return "betterhrbust-development-secret"
	}
	return hex.EncodeToString(buf)
}
