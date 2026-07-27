#!/usr/bin/env bash
set -euo pipefail
umask 077

readonly SCRIPT_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=recovery-common.sh
source "${SCRIPT_DIRECTORY}/recovery-common.sh"

if [[ "$#" -gt 1 ]]; then
  echo "用法：demo-recovery-drill.sh [脱敏报告绝对路径]" >&2
  exit 2
fi

readonly REPORT_FILE="${1:-${RECOVERY_REPOSITORY_ROOT}/project/evidence/artifacts/WP-0007-recovery-drill.json}"
readonly BACKUP_DIRECTORY="$(
  mktemp -d "${RECOVERY_BACKUP_PREFIX}XXXXXX"
)"
readonly RECOVERY_NAME="trusted-rag-foundation-v1-recovery-$(
  date -u +%Y%m%d%H%M%S
)-$$"
RECOVERY_CREATED=false

cleanup_drill() {
  local exit_code=$?
  if [[ "${RECOVERY_CREATED}" == "true" ]]; then
    "${SCRIPT_DIRECTORY}/demo-recovery-cleanup.sh" "${RECOVERY_NAME}" \
      >/dev/null 2>&1 || true
  fi
  if [[ -d "${BACKUP_DIRECTORY}" ]] &&
    [[ "${BACKUP_DIRECTORY}" == "${RECOVERY_BACKUP_PREFIX}"* ]]; then
    rm -rf -- "${BACKUP_DIRECTORY}"
  fi
  exit "${exit_code}"
}

trap cleanup_drill EXIT

if [[ "${REPORT_FILE}" != /* ]]; then
  recovery_fail "脱敏报告路径必须是绝对路径"
  exit 1
fi

"${SCRIPT_DIRECTORY}/demo-backup.sh" "${BACKUP_DIRECTORY}"
RECOVERY_CREATED=true
"${SCRIPT_DIRECTORY}/demo-restore.sh" \
  "${BACKUP_DIRECTORY}" \
  "${RECOVERY_NAME}"
FOUNDATION_RECOVERY_REPORT="${BACKUP_DIRECTORY}/recovery-report.json" \
  "${SCRIPT_DIRECTORY}/demo-recovery-verify.sh" \
  "${BACKUP_DIRECTORY}" \
  "${RECOVERY_NAME}"
"${SCRIPT_DIRECTORY}/demo-recovery-cleanup.sh" "${RECOVERY_NAME}"
RECOVERY_CREATED=false

mkdir -p "$(dirname "${REPORT_FILE}")"
install -m 0644 \
  "${BACKUP_DIRECTORY}/recovery-report.json" \
  "${REPORT_FILE}"

trap - EXIT
rm -rf -- "${BACKUP_DIRECTORY}"

echo "演示备份与隔离恢复演练通过，脱敏报告：${REPORT_FILE}"
