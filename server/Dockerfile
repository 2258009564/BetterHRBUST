# ---------- 构建阶段 ----------
FROM golang:1.26-alpine AS builder

WORKDIR /src

# 先复制依赖清单，利用镜像层缓存避免每次改代码都重新下载依赖
COPY go.mod go.sum ./
RUN go mod download

COPY . .

# 静态编译：产出服务端与初始化命令两个二进制
RUN CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags="-s -w" -o /out/betterhrbust-server ./cmd/server \
 && CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags="-s -w" -o /out/seed-admin ./cmd/seed-admin

# ---------- 运行阶段 ----------
FROM alpine:3.21

RUN apk add --no-cache ca-certificates tzdata wget \
 && adduser -D -u 10001 -s /sbin/nologin app

COPY --from=builder /out/betterhrbust-server /usr/local/bin/betterhrbust-server
COPY --from=builder /out/seed-admin /usr/local/bin/seed-admin

USER app
WORKDIR /app

ENV PORT=9191
EXPOSE 9191

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -qO- "http://127.0.0.1:${PORT}/api/healthz" >/dev/null 2>&1 || exit 1

# 初始超级管理员由命令行创建（容器内直接执行）：
#   docker compose exec server seed-admin -username admin -nickname 超级管理员
CMD ["betterhrbust-server"]
