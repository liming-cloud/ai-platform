#!/bin/sh
set -eu

command -v openssl >/dev/null 2>&1 || {
  printf 'openssl is required to generate deploy secrets\n' >&2
  exit 1
}
command -v python3 >/dev/null 2>&1 || {
  printf 'python3 is required to generate the bootstrap administrator password hash\n' >&2
  exit 1
}

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
deploy_dir=$(dirname "$script_dir")
template="$deploy_dir/.env.example"
target="${1:-$deploy_dir/.env}"

[ -f "$template" ] || {
  printf 'environment template not found: %s\n' "$template" >&2
  exit 1
}
[ ! -e "$target" ] || {
  printf 'refusing to overwrite existing environment file: %s\n' "$target" >&2
  exit 1
}

umask 077
temporary=$(mktemp "${target}.tmp.XXXXXX")
cleanup() {
  rm -f "$temporary"
}
trap cleanup EXIT INT TERM

postgres_password=$(openssl rand -hex 32)
postgres_runtime_password=$(openssl rand -hex 32)
minio_service_secret_key=$(openssl rand -hex 16)
model_encryption_key="b64:$(openssl rand -base64 32 | tr -d '\n')"
invitation_signing_key=$(openssl rand -hex 32)
invitation_outbox_encryption_key="b64:$(openssl rand -base64 32 | tr -d '\n')"
invitation_outbox_kms_key_id="kms-invitation-local-$(openssl rand -hex 8)"
model_kms_key_id="kms-local-$(openssl rand -hex 8)"
bootstrap_admin_password=$(openssl rand -hex 24)
bootstrap_admin_password_hash=$(
  AIP_BOOTSTRAP_ADMIN_PASSWORD="$bootstrap_admin_password" python3 - <<'PY'
import base64
import hashlib
import os

password = os.environ["AIP_BOOTSTRAP_ADMIN_PASSWORD"].encode("utf-8")
salt = os.urandom(16)
iterations = 120_000
digest = hashlib.pbkdf2_hmac("sha256", password, salt, iterations, dklen=32)
print(
    "pbkdf2-sha256$"
    + str(iterations)
    + "$"
    + base64.b64encode(salt).decode("ascii")
    + "$"
    + base64.b64encode(digest).decode("ascii")
)
PY
)

sed \
  -e "s|replace-postgres-password-with-random-characters|$postgres_password|" \
  -e "s|replace-postgres-runtime-password-with-random-characters|$postgres_runtime_password|" \
  -e "s|replace-minio-service-secret-key-with-random-characters|$minio_service_secret_key|" \
  -e "s|replace-model-encryption-key-with-random-characters|$model_encryption_key|" \
  -e "s|replace-invitation-signing-key-with-random-characters|$invitation_signing_key|" \
  -e "s|replace-invitation-outbox-encryption-key-with-random-characters|$invitation_outbox_encryption_key|" \
  -e "s|replace-invitation-outbox-kms-key-id-with-unique-identifier|$invitation_outbox_kms_key_id|" \
  -e "s|replace-model-kms-key-id-with-unique-identifier|$model_kms_key_id|" \
  -e "s|replace-bootstrap-admin-password-with-random-characters|$bootstrap_admin_password|" \
  -e "s|replace-bootstrap-admin-password-hash|$bootstrap_admin_password_hash|" \
  "$template" > "$temporary"

mv "$temporary" "$target"
trap - EXIT INT TERM
printf 'generated secure local environment: %s\n' "$target"
