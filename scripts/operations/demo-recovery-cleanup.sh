#!/usr/bin/env bash
set -euo pipefail

readonly SCRIPT_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=recovery-common.sh
source "${SCRIPT_DIRECTORY}/recovery-common.sh"

if [[ "$#" -ne 1 ]]; then
  echo "用法：demo-recovery-cleanup.sh <受控恢复项目名>" >&2
  exit 2
fi

readonly RECOVERY_NAME="$1"
readonly SOURCE_ENV_FILE="${FOUNDATION_SOURCE_ENV_FILE:-${RECOVERY_DEFAULT_ENV_FILE}}"

require_recovery_command docker
load_recovery_environment "${SOURCE_ENV_FILE}"
export_recovery_environment "${RECOVERY_NAME}"

recovery_compose down --volumes --remove-orphans

for volume_name in \
  "${FOUNDATION_POSTGRES_VOLUME_NAME}" \
  "${FOUNDATION_REDIS_VOLUME_NAME}" \
  "${FOUNDATION_MINIO_VOLUME_NAME}"; do
  if docker volume inspect "${volume_name}" >/dev/null 2>&1; then
    recovery_fail "恢复卷清理失败：${volume_name}"
    exit 1
  fi
done

if docker network inspect "${FOUNDATION_NETWORK_NAME}" >/dev/null 2>&1; then
  recovery_fail "恢复网络清理失败：${FOUNDATION_NETWORK_NAME}"
  exit 1
fi

wait_for_http_endpoint "http://127.0.0.1:18080/actuator/health/readiness"
wait_for_http_endpoint "http://127.0.0.1:18081/actuator/health/readiness"
wait_for_http_endpoint "http://127.0.0.1:18082/actuator/health/readiness"
wait_for_http_endpoint "http://127.0.0.1:13000/healthz"
wait_for_http_endpoint "http://127.0.0.1:13001/healthz"

echo "隔离恢复环境已清理，源环境保持健康"
