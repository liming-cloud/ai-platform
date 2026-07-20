FROM public.ecr.aws/docker/library/node:24.18.0-alpine3.24@sha256:a0b9bf06e4e6193cf7a0f58816cc935ff8c2a908f81e6f1a95432d679c54fbfd AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
ARG VITE_LOCAL_DETERMINISTIC_PROVIDER_ENABLED=false
ENV VITE_LOCAL_DETERMINISTIC_PROVIDER_ENABLED=${VITE_LOCAL_DETERMINISTIC_PROVIDER_ENABLED}
RUN npm run build

FROM docker.io/library/nginx:1.30.4-alpine3.24-slim@sha256:ddde39c6e51f02fde7410c2e9c234cf2d0a4c7bdbbe176aeb37d8ad7ab4eb58c
ARG AIP_SOURCE_REVISION=unknown
LABEL org.opencontainers.image.revision="${AIP_SOURCE_REVISION}"
RUN chown -R nginx:nginx /var/cache/nginx /run /etc/nginx/conf.d /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build --chown=nginx:nginx /app/dist /usr/share/nginx/html
USER 101:101
EXPOSE 8080
