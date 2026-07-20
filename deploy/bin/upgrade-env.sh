#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
deploy_dir=$(dirname "$script_dir")
initializer="$script_dir/init-env.sh"
validator="$script_dir/validate-env.sh"
target="${1:-$deploy_dir/.env}"

[ -f "$target" ] || {
  printf 'environment file not found: %s\n' "$target" >&2
  exit 1
}
command -v python3 >/dev/null 2>&1 || {
  printf 'python3 is required to upgrade the local environment\n' >&2
  exit 1
}

umask 077
temporary_dir=$(mktemp -d "${target}.upgrade.XXXXXX")
generated="$temporary_dir/generated.env"
merged="$temporary_dir/merged.env"
cleanup() {
  rm -f "$generated" "$merged"
  rmdir "$temporary_dir"
}
trap cleanup EXIT INT TERM

sh "$initializer" "$generated" >/dev/null

python3 - "$target" "$generated" "$merged" <<'PY'
import base64
import hashlib
import hmac
import re
import sys
from pathlib import Path

target_path, generated_path, merged_path = map(Path, sys.argv[1:])
managed_minimums = {
    "AIP_POSTGRES_PASSWORD": 24,
    "AIP_POSTGRES_RUNTIME_PASSWORD": 24,
    "AIP_MINIO_SERVICE_SECRET_KEY": 24,
    "AIP_IDENTITY_INVITATION_SIGNING_KEY": 32,
    "AIP_IDENTITY_INVITATION_OUTBOX_KMS_KEY_ID": 1,
    "AIP_IDENTITY_INVITATION_OUTBOX_ENCRYPTION_KEY": 32,
    "AIP_MODEL_HUB_SECRET_KMS_KEY_ID": 1,
    "AIP_MODEL_HUB_SECRET_ENCRYPTION_KEY": 32,
}
managed_maximums = {
    "AIP_MINIO_SERVICE_SECRET_KEY": 40,
}
removed_names = {
    "AIP_LOCAL_ACCEPTANCE_PASSWORD",
    "AIP_LOCAL_ACCEPTANCE_PASSWORD_HASH",
}
bootstrap_password_name = "AIP_BOOTSTRAP_ADMIN_PASSWORD"
bootstrap_hash_name = "AIP_BOOTSTRAP_ADMIN_PASSWORD_HASH"
weak_prefixes = ("local-dev", "replace-", "change-", "changeme", "example", "password")
weak_exact = {"aip", "aipminio", "aipminio123"}
assignment_pattern = re.compile(r"^([A-Za-z_][A-Za-z0-9_]*)=(.*)$")


def decoded(raw_value: str) -> str:
    value = raw_value.strip()
    if len(value) >= 2 and value[0] == value[-1] and value[0] in {"'", '"'}:
        return value[1:-1]
    return value


def assignments(path: Path):
    result = {}
    order = []
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        match = assignment_pattern.match(raw_line.strip())
        if not match:
            continue
        name, raw_value = match.groups()
        if name not in result:
            order.append(name)
        result[name] = (raw_line, decoded(raw_value))
    return result, order


def is_weak(value: str) -> bool:
    comparable = value.lower()
    return comparable in weak_exact or comparable.startswith(weak_prefixes)


def is_strong_managed(name: str, value: str) -> bool:
    if not value or is_weak(value) or len(value) < managed_minimums[name]:
        return False
    if name in managed_maximums and len(value) > managed_maximums[name]:
        return False
    if name in {"AIP_MODEL_HUB_SECRET_KMS_KEY_ID", "AIP_IDENTITY_INVITATION_OUTBOX_KMS_KEY_ID"}:
        return re.fullmatch(r"[A-Za-z0-9_-]+", value) is not None
    if name in {"AIP_MODEL_HUB_SECRET_ENCRYPTION_KEY", "AIP_IDENTITY_INVITATION_OUTBOX_ENCRYPTION_KEY"}:
        if len(value) > 512:
            return False
        if value.startswith("b64:"):
            try:
                decoded_key = base64.b64decode(value[4:], validate=True)
            except ValueError:
                return False
            return len(decoded_key) == 32 and len(set(decoded_key)) > 1
        return len(value.encode("utf-8")) <= 256
    return True


def valid_bootstrap_pair(password: str, stored_hash: str) -> bool:
    if not password or is_weak(password) or len(password) < 24:
        return False
    try:
        algorithm, iterations_text, salt_text, digest_text = stored_hash.split("$")
        iterations = int(iterations_text)
        salt = base64.b64decode(salt_text, validate=True)
        expected = base64.b64decode(digest_text, validate=True)
    except (ValueError, TypeError):
        return False
    if algorithm != "pbkdf2-sha256" or iterations < 120_000 or not salt or not expected:
        return False
    actual = hashlib.pbkdf2_hmac(
        "sha256", password.encode("utf-8"), salt, iterations, len(expected)
    )
    return hmac.compare_digest(actual, expected)


existing, existing_order = assignments(target_path)
fresh, _ = assignments(generated_path)
required_generated = set(managed_minimums) | {
    bootstrap_password_name,
    bootstrap_hash_name,
}
if not required_generated.issubset(fresh):
    raise SystemExit("generated environment is missing required managed values")
preserve = (
    set(existing)
    - set(managed_minimums)
    - removed_names
    - {bootstrap_password_name, bootstrap_hash_name}
)
for name in managed_minimums:
    if name in existing and is_strong_managed(name, existing[name][1]):
        preserve.add(name)
if (
    bootstrap_password_name in existing
    and bootstrap_hash_name in existing
    and valid_bootstrap_pair(
        existing[bootstrap_password_name][1],
        existing[bootstrap_hash_name][1],
    )
):
    preserve.update({bootstrap_password_name, bootstrap_hash_name})

merged_lines = []
written = set()
for raw_line in generated_path.read_text(encoding="utf-8").splitlines():
    match = assignment_pattern.match(raw_line.strip())
    if not match:
        merged_lines.append(raw_line)
        continue
    name = match.group(1)
    if name in preserve:
        merged_lines.append(existing[name][0])
    else:
        merged_lines.append(raw_line)
    written.add(name)

for name in existing_order:
    if name not in written and name not in removed_names:
        merged_lines.append(existing[name][0])

merged_path.write_text("\n".join(merged_lines) + "\n", encoding="utf-8")
PY

chmod 600 "$merged"

python3 - "$merged" "$validator" <<'PY'
import os
import re
import subprocess
import sys
from pathlib import Path

environment_path = Path(sys.argv[1])
validator = sys.argv[2]
assignment_pattern = re.compile(r"^([A-Za-z_][A-Za-z0-9_]*)=(.*)$")
environment = {"PATH": os.environ.get("PATH", "")}
for raw_line in environment_path.read_text(encoding="utf-8").splitlines():
    match = assignment_pattern.match(raw_line.strip())
    if not match:
        continue
    name, value = match.groups()
    value = value.strip()
    if len(value) >= 2 and value[0] == value[-1] and value[0] in {"'", '"'}:
        value = value[1:-1]
    environment[name] = value

result = subprocess.run(["sh", validator], env=environment, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
if result.returncode != 0:
    raise SystemExit("upgraded environment failed deploy preflight")
PY

if docker compose version >/dev/null 2>&1; then
  docker compose --env-file "$merged" -f "$deploy_dir/docker-compose.yml" config --quiet >/dev/null
fi

mv "$merged" "$target"
chmod 600 "$target"
trap - EXIT INT TERM
rm -f "$generated"
rmdir "$temporary_dir"
printf 'upgraded secure local environment: %s\n' "$target"
