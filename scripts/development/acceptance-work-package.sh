#!/usr/bin/env bash
set -euo pipefail

# 切片绿灯不等于整包交付；未知范围必须在启动浏览器前拒绝。
if [[ "${1:-}" != "WP-0017" ]]; then
  echo "不支持的工作包，当前只实现 WP-0017 的明确独立切片。" >&2
  exit 2
fi
if [[ ( "${2:-}" != "login" && "${2:-}" != "onboarding" && "${2:-}" != "admins" && "${2:-}" != "quotas" && "${2:-}" != "features" && "${2:-}" != "security" && "${2:-}" != "accounts" && "${2:-}" != "roles" ) || "$#" != "2" ]]; then
  echo "WP-0017 整包尚未实现；必须明确 SLICE=login、SLICE=onboarding、SLICE=admins、SLICE=quotas、SLICE=features、SLICE=security、SLICE=accounts 或 SLICE=roles。" >&2
  exit 2
fi
if [[ -n "${SETUP:-}" && "${SETUP}" != "tenant" ]]; then
  echo "SETUP 只允许 tenant，且不授权重置或删除数据。" >&2
  exit 2
fi
if [[ -z "${FOUNDATION_ACCEPTANCE_CREDENTIALS_FILE:-}" ]]; then
  echo "必须提供 0600 验收凭据文件：FOUNDATION_ACCEPTANCE_CREDENTIALS_FILE。" >&2
  exit 2
fi

readonly SCRIPT_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec node "${SCRIPT_DIRECTORY}/acceptance-${2}.cjs"
