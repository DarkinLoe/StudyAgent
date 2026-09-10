# ---------- 构建阶段：Node 构建出静态产物 ----------
FROM node:22-alpine AS build
WORKDIR /app

# 先装依赖（利用层缓存）
COPY package*.json ./
RUN npm ci --no-audit --no-fund

COPY . .
RUN npm run build

# ---------- 运行阶段：Nginx 托管静态文件并反代后端 ----------
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
