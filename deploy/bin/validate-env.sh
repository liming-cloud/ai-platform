#!/bin/sh
set -eu

fail() {
  printf 'deploy preflight failed: %s\n' "$1" >&2
  exit 1
}

require_value() {
  name="$1"
  value="$2"
  [ -n "$value" ] || fail "$name is required"
}

require_secret() {
  name="$1"
  value="$2"
  minimum_length="$3"
  require_value "$name" "$value"

  comparable=$(printf '%s' "$value" | tr '[:upper:]' '[:lower:]')
  case "$comparable" in
    local-dev*|replace-*|change-*|changeme*|example*|password*|aip|aipminio|aipminio123)
      fail "$name still contains an example or known weak value"
      ;;
  esac

  actual_length=$(printf '%s' "$value" | wc -c | tr -d ' ')
  [ "$actual_length" -ge "$minimum_length" ] || fail "$name must contain at least $minimum_length characters"
}

require_identifier() {
  name="$1"
  value="$2"
  require_value "$name" "$value"
  case "$value" in
    *[!a-zA-Z0-9_-]*) fail "$name may only contain letters, digits, underscores, and hyphens" ;;
  esac
}

require_postgres_identifier() {
  name="$1"
  value="$2"
  require_value "$name" "$value"
  case "$value" in
    [a-zA-Z_]*) ;;
    *) fail "$name must start with a letter or underscore" ;;
  esac
  case "$value" in
    *[!a-zA-Z0-9_]*) fail "$name must be a portable PostgreSQL identifier" ;;
  esac
}

require_secure_identifier() {
  name="$1"
  value="$2"
  require_identifier "$name" "$value"
  comparable=$(printf '%s' "$value" | tr '[:upper:]' '[:lower:]')
  case "$comparable" in
    local-dev*|replace-*|change-*|changeme*|example*|password*|aip|aipminio|aipminio123)
      fail "$name still contains an example or known weak value"
      ;;
  esac
}

require_bounded_integer() {
  name="$1"
  value="$2"
  minimum="$3"
  maximum="$4"
  case "$value" in
    ''|*[!0-9]*) fail "$name must be an integer" ;;
  esac
  [ "$value" -ge "$minimum" ] && [ "$value" -le "$maximum" ] \
    || fail "$name must be between $minimum and $maximum"
}

require_boolean() {
  name="$1"
  value="$2"
  case "$value" in
    true|false) ;;
    *) fail "$name must be true or false" ;;
  esac
}

require_loopback_bind_address() {
  name="$1"
  value="${2:-127.0.0.1}"
  case "$value" in
    127.0.0.1) ;;
    *) fail "$name must remain 127.0.0.1; the deploy baseline does not expose unauthenticated services" ;;
  esac
}

require_key_material() {
  name="$1"
  value="$2"
  require_secret "$name" "$value" 32

  material_length=$(printf '%s' "$value" | wc -c | tr -d ' ')
  [ "$material_length" -le 512 ] || fail "$name must contain at most 512 characters"
  case "$value" in
    b64:*)
      encoded=${value#b64:}
      decoded_file="/tmp/aip-model-key-$$"
      if ! printf '%s' "$encoded" | base64 -d >"$decoded_file" 2>/dev/null; then
        rm -f "$decoded_file"
        fail "$name must contain valid Base64 after b64:"
      fi
      decoded_length=$(wc -c <"$decoded_file" | tr -d ' ')
      if [ "$decoded_length" -gt 0 ]; then
        distinct_bytes=$(od -An -tu1 "$decoded_file" | tr ' ' '\n' | sed '/^$/d' | sort -u | wc -l | tr -d ' ')
      else
        distinct_bytes=0
      fi
      rm -f "$decoded_file"
      [ "$decoded_length" -eq 32 ] || fail "$name must decode to exactly 32 bytes"
      [ "$distinct_bytes" -gt 1 ] || fail "$name must not use obviously weak key material"
      ;;
    *)
      [ "$material_length" -le 256 ] || fail "$name legacy material must contain at most 256 bytes"
      ;;
  esac
}

require_previous_keys() {
  previous_name="$1"
  active_key_id="$2"
  value="$3"
  [ -n "$value" ] || return 0

  total_length=$(printf '%s' "$value" | wc -c | tr -d ' ')
  [ "$total_length" -le 16384 ] || fail "$previous_name is too long"
  case "$value" in
    ,*|*,|*,,*) fail "$previous_name contains an empty entry" ;;
  esac

  previous_ifs=$IFS
  IFS=','
  set -- $value
  IFS=$previous_ifs
  [ "$#" -le 32 ] || fail "$previous_name contains too many entries"

  seen=','
  for entry in "$@"; do
    case "$entry" in
      *=*) ;;
      *) fail "$previous_name entry must use keyId=keyMaterial" ;;
    esac
    key_id=${entry%%=*}
    key_material=${entry#*=}
    require_secure_identifier "${previous_name}_KEY_ID" "$key_id"
    key_id_length=$(printf '%s' "$key_id" | wc -c | tr -d ' ')
    [ "$key_id_length" -le 128 ] || fail 'historical model key ID is too long'
    [ "$key_id" != "$active_key_id" ] || fail 'historical model key ID duplicates the active key ID'
    case "$seen" in
      *",$key_id,"*) fail 'historical model key ID is duplicated' ;;
    esac
    seen="$seen$key_id,"
    require_key_material "${previous_name}_KEY" "$key_material"
  done
}

require_password_hash() {
  name="$1"
  value="$2"
  require_value "$name" "$value"

  comparable=$(printf '%s' "$value" | tr '[:upper:]' '[:lower:]')
  case "$comparable" in
    local-dev*|replace-*|change-*|changeme*|example*|password*)
      fail "$name still contains an example or known weak value"
      ;;
  esac

  previous_ifs=$IFS
  IFS='$'
  set -- $value
  IFS=$previous_ifs
  [ "$#" -eq 4 ] || fail "$name must use the pbkdf2-sha256 storage format"
  [ "$1" = "pbkdf2-sha256" ] || fail "$name must use the pbkdf2-sha256 storage format"
  case "$2" in
    ''|*[!0-9]*) fail "$name must contain a numeric iteration count" ;;
  esac
  [ "$2" -ge 120000 ] || fail "$name must use at least 120000 PBKDF2 iterations"
  case "$3" in
    ''|*[!a-zA-Z0-9+/=]*) fail "$name contains an invalid Base64 salt" ;;
  esac
  case "$4" in
    ''|*[!a-zA-Z0-9+/=]*) fail "$name contains an invalid Base64 digest" ;;
  esac
}

require_loopback_bind_address AIP_BIND_ADDRESS "${AIP_BIND_ADDRESS:-}"
require_identifier AIP_POSTGRES_DB "${AIP_POSTGRES_DB:-}"
require_identifier AIP_POSTGRES_USER "${AIP_POSTGRES_USER:-}"
require_secret AIP_POSTGRES_PASSWORD "${AIP_POSTGRES_PASSWORD:-}" 24
require_postgres_identifier AIP_POSTGRES_RUNTIME_USER "${AIP_POSTGRES_RUNTIME_USER:-}"
runtime_role_length=$(printf '%s' "${AIP_POSTGRES_RUNTIME_USER:-}" | wc -c | tr -d ' ')
[ "$runtime_role_length" -le 63 ] \
  || fail 'AIP_POSTGRES_RUNTIME_USER exceeds the PostgreSQL identifier limit'
require_secret AIP_POSTGRES_RUNTIME_PASSWORD "${AIP_POSTGRES_RUNTIME_PASSWORD:-}" 24
[ "${AIP_POSTGRES_RUNTIME_USER:-}" != "${AIP_POSTGRES_USER:-}" ] \
  || fail 'AIP_POSTGRES_RUNTIME_USER must differ from AIP_POSTGRES_USER'
[ "${AIP_POSTGRES_RUNTIME_PASSWORD:-}" != "${AIP_POSTGRES_PASSWORD:-}" ] \
  || fail 'AIP_POSTGRES_RUNTIME_PASSWORD must differ from AIP_POSTGRES_PASSWORD'
require_secure_identifier AIP_MINIO_SERVICE_ACCESS_KEY "${AIP_MINIO_SERVICE_ACCESS_KEY:-}"
require_secret AIP_MINIO_SERVICE_SECRET_KEY "${AIP_MINIO_SERVICE_SECRET_KEY:-}" 24
minio_service_secret_length=$(printf '%s' "${AIP_MINIO_SERVICE_SECRET_KEY:-}" | wc -c | tr -d ' ')
[ "$minio_service_secret_length" -le 40 ] \
  || fail 'AIP_MINIO_SERVICE_SECRET_KEY must contain at most 40 characters'
require_secure_identifier AIP_MODEL_HUB_SECRET_KMS_KEY_ID "${AIP_MODEL_HUB_SECRET_KMS_KEY_ID:-}"
model_key_id_length=$(printf '%s' "${AIP_MODEL_HUB_SECRET_KMS_KEY_ID:-}" | wc -c | tr -d ' ')
[ "$model_key_id_length" -le 128 ] || fail 'AIP_MODEL_HUB_SECRET_KMS_KEY_ID is too long'
require_key_material AIP_MODEL_HUB_SECRET_ENCRYPTION_KEY "${AIP_MODEL_HUB_SECRET_ENCRYPTION_KEY:-}"
require_previous_keys \
  AIP_MODEL_HUB_SECRET_PREVIOUS_KEYS \
  "${AIP_MODEL_HUB_SECRET_KMS_KEY_ID:-}" \
  "${AIP_MODEL_HUB_SECRET_PREVIOUS_KEYS:-}"
require_boolean \
  AIP_MODEL_HUB_SECRET_ROTATION_ENABLED \
  "${AIP_MODEL_HUB_SECRET_ROTATION_ENABLED:-false}"
require_boolean \
  AIP_MODEL_HUB_LOCAL_DETERMINISTIC_ENABLED \
  "${AIP_MODEL_HUB_LOCAL_DETERMINISTIC_ENABLED-}"
require_bounded_integer \
  AIP_MODEL_HUB_SECRET_ROTATION_BATCH_SIZE \
  "${AIP_MODEL_HUB_SECRET_ROTATION_BATCH_SIZE:-20}" \
  1 \
  100
require_bounded_integer \
  AIP_MODEL_HUB_SECRET_ROTATION_POLL_DELAY_MS \
  "${AIP_MODEL_HUB_SECRET_ROTATION_POLL_DELAY_MS:-5000}" \
  1000 \
  3600000
require_secret AIP_IDENTITY_INVITATION_SIGNING_KEY "${AIP_IDENTITY_INVITATION_SIGNING_KEY:-}" 32
require_secure_identifier \
  AIP_IDENTITY_INVITATION_OUTBOX_KMS_KEY_ID \
  "${AIP_IDENTITY_INVITATION_OUTBOX_KMS_KEY_ID:-}"
invitation_outbox_key_id_length=$(printf '%s' "${AIP_IDENTITY_INVITATION_OUTBOX_KMS_KEY_ID:-}" | wc -c | tr -d ' ')
[ "$invitation_outbox_key_id_length" -le 128 ] \
  || fail 'AIP_IDENTITY_INVITATION_OUTBOX_KMS_KEY_ID is too long'
require_key_material \
  AIP_IDENTITY_INVITATION_OUTBOX_ENCRYPTION_KEY \
  "${AIP_IDENTITY_INVITATION_OUTBOX_ENCRYPTION_KEY:-}"
require_previous_keys \
  AIP_IDENTITY_INVITATION_OUTBOX_PREVIOUS_KEYS \
  "${AIP_IDENTITY_INVITATION_OUTBOX_KMS_KEY_ID:-}" \
  "${AIP_IDENTITY_INVITATION_OUTBOX_PREVIOUS_KEYS:-}"
require_secret AIP_BOOTSTRAP_ADMIN_PASSWORD "${AIP_BOOTSTRAP_ADMIN_PASSWORD:-}" 24
require_password_hash AIP_BOOTSTRAP_ADMIN_PASSWORD_HASH "${AIP_BOOTSTRAP_ADMIN_PASSWORD_HASH:-}"
case "${AIP_OBJECT_STORAGE_BUCKET:-}" in
  ''|*[!a-z0-9.-]*|.*|-*|*-) fail 'AIP_OBJECT_STORAGE_BUCKET must be a valid lowercase S3 bucket name' ;;
esac

printf 'deploy preflight passed\n'
