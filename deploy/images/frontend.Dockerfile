# 前端构建使用固定 Node 镜像与 npm 锁文件。
FROM node:22.22.3-alpine3.24@sha256:e58326d0d441090181ac150dc2078d3e2cf6a0d42e809aebba3ef5880935ffdd AS builder

ARG APP
WORKDIR /workspace

COPY frontend/package.json frontend/package-lock.json ./
COPY frontend/apps/console-web/package.json apps/console-web/package.json
COPY frontend/apps/query-web/package.json apps/query-web/package.json
COPY frontend/packages/design-system/package.json packages/design-system/package.json
RUN npm ci

COPY frontend .

# 应用名使用白名单，确保每个镜像只包含一个前端产物。
RUN case "${APP}" in \
      console-web|query-web) ;; \
      *) echo "不支持的前端应用: ${APP}" >&2; exit 1 ;; \
    esac \
    && npm run build --workspace "@foundation/${APP}"

FROM nginxinc/nginx-unprivileged:1.29.8-alpine3.23@sha256:0c79d56aee561a1d81c63f00eee5fb5fe29279560cdc55e91425133104c7fbe6

ARG APP
COPY deploy/config/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder "/workspace/apps/${APP}/dist/app" /usr/share/nginx/html

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=6 \
  CMD wget -q -O - http://127.0.0.1:8080/healthz >/dev/null || exit 1
