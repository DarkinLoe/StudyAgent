# 全局 ARG：用于 FROM 行，必须声明在第一个 FROM 之前
# 便于在镜像源受限/某镜像异常时切换运行时基础镜像，例如：
#   docker compose --profile app build --build-arg NGINX_IMAGE=nginx:1.27-alpine web
# 默认 Debian 版：比 alpine 略大，但兼容性更好
# （实测某环境下 nginx:1.27-alpine 会 exec format error，而 nginx:1.27 正常）
ARG NGINX_IMAGE=nginx:1.27

# ---------- 构建阶段：Node 构建出静态产物 ----------
FROM node:22-alpine AS build
WORKDIR /app

# 先装依赖（利用层缓存）
COPY package*.json ./
RUN npm ci --no-audit --no-fund

COPY . .
RUN npm run build

# ---------- 运行阶段：Nginx 托管静态文件并反代后端 ----------
FROM ${NGINX_IMAGE}

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
