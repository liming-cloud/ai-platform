#!/usr/bin/env bash
set -euo pipefail

# 整包集成只读复验与独立写入切片分开；未知范围必须在启动浏览器前拒绝。
if [[ "${1:-}" == "WP-0018" ]]; then
  if [[ "$#" != "2" || ( -n "${2:-}" && "${2}" != "account" && "${2}" != "features" && "${2}" != "assistants" && "${2}" != "roles" ) ]]; then
    echo "不支持的 WP-0018 验收范围：留空执行整包只读集成复验，或明确 SLICE=account、features、assistants、roles；切片不等于整包完成。" >&2
    exit 2
  fi
  if [[ -n "${SETUP:-}" ]]; then
    echo "WP-0018 验收由各切片限定本轮对象准备，不接受外部 SETUP，也不授权重置或删除历史数据。" >&2
    exit 2
  fi
  if [[ -z "${FOUNDATION_ACCEPTANCE_CREDENTIALS_FILE:-}" ]]; then
    echo "必须提供 0600 验收凭据文件：FOUNDATION_ACCEPTANCE_CREDENTIALS_FILE。" >&2
    exit 2
  fi
  readonly MEMBER_SCRIPT_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  if [[ -z "${2}" ]]; then
    exec node "${MEMBER_SCRIPT_DIRECTORY}/acceptance-member-package.cjs"
  fi
  if [[ "${2}" == "roles" ]]; then
    exec node "${MEMBER_SCRIPT_DIRECTORY}/acceptance-member-roles.cjs"
  fi
  if [[ "${2}" == "assistants" ]]; then
    exec node "${MEMBER_SCRIPT_DIRECTORY}/acceptance-member-authorization.cjs"
  fi
  if [[ "${2}" == "features" ]]; then
    exec node "${MEMBER_SCRIPT_DIRECTORY}/acceptance-member-features.cjs"
  fi
  exec node "${MEMBER_SCRIPT_DIRECTORY}/acceptance-member-account.cjs"
fi
if [[ "${1:-}" != "WP-0017" ]]; then
  echo "不支持的工作包，当前只实现 WP-0017 与 WP-0018 的明确独立切片。" >&2
  exit 2
fi
if [[ ( "${2:-}" != "login" && "${2:-}" != "login-entries" && "${2:-}" != "onboarding" && "${2:-}" != "admins" && "${2:-}" != "quotas" && "${2:-}" != "features" && "${2:-}" != "security" && "${2:-}" != "accounts" && "${2:-}" != "roles" && "${2:-}" != "overview" && "${2:-}" != "tenants" ) || "$#" != "2" ]]; then
  echo "本入口只执行独立切片，整包收口依据任务范围的完整组合证据；必须明确 SLICE=login、SLICE=login-entries、SLICE=onboarding、SLICE=admins、SLICE=quotas、SLICE=features、SLICE=security、SLICE=accounts、SLICE=roles、SLICE=overview 或 SLICE=tenants。" >&2
  exit 2
fi
if [[ "${2}" == "overview" && -n "${SETUP:-}" ]]; then
  echo "概览切片只读，不提供业务对象准备。" >&2
  exit 2
fi
if [[ "${2}" == "tenants" && -n "${SETUP:-}" ]]; then
  echo "目录切片只读，不提供业务对象准备。" >&2
  exit 2
fi
if [[ "${2}" == "login-entries" && -n "${SETUP:-}" ]]; then
  echo "登录导航切片只读，不提供业务对象准备。" >&2
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
