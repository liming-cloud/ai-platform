#!/usr/bin/env bash
set -euo pipefail
umask 077

readonly SCRIPT_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=recovery-common.sh
source "${SCRIPT_DIRECTORY}/recovery-common.sh"

if [[ "$#" -ne 2 ]]; then
  echo "用法：demo-resilience-verify.sh <受控恢复项目名> <报告绝对路径>" >&2
  exit 2
fi

readonly RECOVERY_NAME="$1"
readonly REPORT_FILE="$2"
readonly SOURCE_ENV_FILE="${FOUNDATION_SOURCE_ENV_FILE:-${RECOVERY_DEFAULT_ENV_FILE}}"

if [[ "${REPORT_FILE}" != /* ]]; then
  recovery_fail "故障恢复报告路径必须是绝对路径"
  exit 1
fi

load_recovery_environment "${SOURCE_ENV_FILE}"
export_recovery_environment "${RECOVERY_NAME}"

if [[ "$(recovery_compose ps --status running --services | wc -l | tr -d ' ')" -ne 8 ]]; then
  recovery_fail "故障演练前隔离环境未运行全部八个服务"
  exit 1
fi

wait_for_postgres() {
  local attempt
  for ((attempt = 1; attempt <= 60; attempt++)); do
    if recovery_compose exec -T postgres \
      pg_isready \
      --username "${FOUNDATION_POSTGRES_USER}" \
      --dbname "${FOUNDATION_POSTGRES_DATABASE}" \
      >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  recovery_fail "PostgreSQL 重启后未恢复健康"
}

wait_for_minio() {
  local attempt
  for ((attempt = 1; attempt <= 60; attempt++)); do
    if recovery_compose exec -T minio mc ready local >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  recovery_fail "MinIO 重启后未恢复健康"
}

wait_for_service() {
  local service="$1"
  case "${service}" in
    postgres)
      wait_for_postgres
      wait_for_http_endpoint \
        "http://127.0.0.1:${FOUNDATION_CONTROL_PLANE_PORT}/actuator/health/readiness"
      wait_for_http_endpoint \
        "http://127.0.0.1:${FOUNDATION_QUERY_RUNTIME_PORT}/actuator/health/readiness"
      wait_for_http_endpoint \
        "http://127.0.0.1:${FOUNDATION_INGESTION_WORKER_PORT}/actuator/health/readiness"
      ;;
    minio)
      wait_for_minio
      ;;
    query-runtime)
      wait_for_http_endpoint \
        "http://127.0.0.1:${FOUNDATION_QUERY_RUNTIME_PORT}/actuator/health/readiness"
      ;;
    ingestion-worker)
      wait_for_http_endpoint \
        "http://127.0.0.1:${FOUNDATION_INGESTION_WORKER_PORT}/actuator/health/readiness"
      ;;
    *)
      recovery_fail "不支持的故障演练服务：${service}"
      ;;
  esac
}

readonly DURATIONS_FILE="$(mktemp "${TMPDIR:-/tmp}/trusted-rag-resilience.XXXXXX")"
cleanup_duration_file() {
  rm -f -- "${DURATIONS_FILE}"
}
trap cleanup_duration_file EXIT

readonly SERVICES=(postgres minio query-runtime ingestion-worker)
for service in "${SERVICES[@]}"; do
  started_at="$(recovery_epoch_seconds)"
  recovery_compose stop --timeout 10 "${service}" >/dev/null
  if recovery_compose ps --status running --services |
    grep -Fxq "${service}"; then
    recovery_fail "服务停止后仍处于运行状态：${service}"
    exit 1
  fi

  recovery_compose start "${service}" >/dev/null
  wait_for_service "${service}"
  completed_at="$(recovery_epoch_seconds)"
  printf '%s\t%s\n' \
    "${service}" \
    "$((completed_at - started_at))" \
    >>"${DURATIONS_FILE}"
done

if [[ "$(recovery_compose ps --status running --services | wc -l | tr -d ' ')" -ne 8 ]]; then
  recovery_fail "故障演练后隔离环境未恢复全部八个服务"
  exit 1
fi

jq -Rn \
  --slurpfile durations <(
    jq -Rn '
      [inputs
        | split("\t")
        | {
            service: .[0],
            recoverySeconds: (.[1] | tonumber)
          }]
    ' <"${DURATIONS_FILE}"
  ) \
  '{
    services: ["postgres", "minio", "query-runtime", "ingestion-worker"],
    allRecovered: true,
    recoveryDurations: $durations[0]
  }' >"${REPORT_FILE}"
chmod 600 "${REPORT_FILE}"

trap - EXIT
cleanup_duration_file
echo "四个关键服务停止与重启演练通过"
