# 注意：本镜像的构建上下文是**仓库根目录**（见 docker-compose.yml 的 context: .），
# 因为 Web 端会直接引用仓库根的 shared/ 共享数据：
#   web/src/views/ResourcesView.vue  → ../../../shared/resources.json
#   web/src/utils/schoolCalendar.js  → ../../../shared/school-calendar.json
# 因此必须保持 /src/web 与 /src/shared 的相对层级关系。

# ---------- 构建阶段 ----------
FROM node:22-alpine AS builder

WORKDIR /src

# 先装依赖，利用层缓存避免每次改代码都重新安装
COPY web/package.json web/package-lock.json ./web/
RUN cd web && npm ci

COPY web ./web
COPY shared ./shared

RUN cd web && npm run build

# ---------- 运行阶段 ----------
FROM nginx:1.27-alpine

COPY web/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /src/web/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
  CMD wget -qO- http://127.0.0.1/ >/dev/null 2>&1 || exit 1
