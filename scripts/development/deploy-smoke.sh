#!/usr/bin/env bash
set -euo pipefail

readonly ENDPOINTS=(
  "http://127.0.0.1:18080/actuator/health/readiness"
  "http://127.0.0.1:18081/actuator/health/readiness"
  "http://127.0.0.1:18082/actuator/health/readiness"
  "http://127.0.0.1:13000/healthz"
  "http://127.0.0.1:13001/healthz"
)

# 每个地址最多等待六十秒，失败时保留 Compose 现场供排查。
for endpoint in "${ENDPOINTS[@]}"; do
  ready=false
  for _ in {1..30}; do
    if curl --fail --silent "${endpoint}" >/dev/null 2>&1; then
      ready=true
      break
    fi
    sleep 2
  done
  if [[ "${ready}" != "true" ]]; then
    echo "健康检查失败: ${endpoint}" >&2
    exit 1
  fi
done

echo "五个应用入口健康检查通过"
