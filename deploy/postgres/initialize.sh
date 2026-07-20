#!/bin/sh
set -eu

: "${PGHOST:?PGHOST is required}"
: "${PGPORT:?PGPORT is required}"
: "${PGDATABASE:?PGDATABASE is required}"
: "${PGUSER:?PGUSER is required}"
: "${PGPASSWORD:?PGPASSWORD is required}"
: "${AIP_POSTGRES_RUNTIME_USER:?AIP_POSTGRES_RUNTIME_USER is required}"
: "${AIP_POSTGRES_RUNTIME_PASSWORD:?AIP_POSTGRES_RUNTIME_PASSWORD is required}"
: "${AIP_BOOTSTRAP_ADMIN_PASSWORD_HASH:?AIP_BOOTSTRAP_ADMIN_PASSWORD_HASH is required}"

case "$AIP_POSTGRES_RUNTIME_USER" in
  [a-zA-Z_]*) ;;
  *)
    printf 'AIP_POSTGRES_RUNTIME_USER must start with a letter or underscore\n' >&2
    exit 1
    ;;
esac
case "$AIP_POSTGRES_RUNTIME_USER" in
  *[!a-zA-Z0-9_]*)
    printf 'AIP_POSTGRES_RUNTIME_USER must be a PostgreSQL identifier\n' >&2
    exit 1
    ;;
esac
[ "${#AIP_POSTGRES_RUNTIME_USER}" -le 63 ] || {
  printf 'AIP_POSTGRES_RUNTIME_USER exceeds the PostgreSQL identifier limit\n' >&2
  exit 1
}
[ "$AIP_POSTGRES_RUNTIME_USER" != "$PGUSER" ] || {
  printf 'runtime database role must differ from the schema owner role\n' >&2
  exit 1
}
[ "$AIP_POSTGRES_RUNTIME_PASSWORD" != "$PGPASSWORD" ] || {
  printf 'runtime database credential must differ from the schema owner credential\n' >&2
  exit 1
}

export PGPASSWORD

psql_run() {
  psql \
    --host "$PGHOST" \
    --port "$PGPORT" \
    --username "$PGUSER" \
    --dbname "$PGDATABASE" \
    --set ON_ERROR_STOP=1 \
    "$@"
}

attempt=0
until pg_isready --host "$PGHOST" --port "$PGPORT" --username "$PGUSER" --dbname "$PGDATABASE" >/dev/null 2>&1; do
  attempt=$((attempt + 1))
  [ "$attempt" -le 60 ] || {
    printf 'postgres did not become ready within 60 seconds\n' >&2
    exit 1
  }
  sleep 1
done

public_table_count=$(
  psql_run --tuples-only --no-align --command \
    "SELECT count(*) FROM pg_tables WHERE schemaname = 'public'" |
    tr -d '[:space:]'
)

if [ "$public_table_count" = "0" ]; then
  printf 'initializing database schema and base data\n'
  {
    printf '\\set ON_ERROR_STOP on\n'
    printf '\\set bootstrap_credentials_only false\n'
    printf 'BEGIN;\n'
    printf "SELECT pg_advisory_xact_lock(hashtext('ai-platform-database-initialize'));\n"
    printf 'DROP SCHEMA public CASCADE;\n'
    printf '\\i /schema/01-schema.sql\n'
    printf 'SET search_path TO public, pg_catalog;\n'
    printf '\\i /schema/02-seed-base.sql\n'
    printf 'COMMIT;\n'
  } | psql_run
elif [ "$(psql_run --tuples-only --no-align --command "SELECT to_regclass('public.iam_tenant') IS NOT NULL" | tr -d '[:space:]')" = "t" ]; then
  printf 'refreshing bootstrap administrator credential hash\n'
  {
    printf '\\set ON_ERROR_STOP on\n'
    printf '\\set bootstrap_credentials_only true\n'
    printf 'BEGIN;\n'
    printf "SELECT pg_advisory_xact_lock(hashtext('ai-platform-database-initialize'));\n"
    printf 'SET search_path TO public, pg_catalog;\n'
    printf '\\i /schema/02-seed-base.sql\n'
    printf 'COMMIT;\n'
  } | psql_run
else
  printf 'database contains tables but is not an initialized AI Platform database\n' >&2
  exit 1
fi

psql_run \
  --set aip_runtime_role="$AIP_POSTGRES_RUNTIME_USER" \
  --set aip_runtime_password="$AIP_POSTGRES_RUNTIME_PASSWORD" \
  --file /runtime-role/configure-runtime-role.sql
psql_run \
  --set aip_runtime_role="$AIP_POSTGRES_RUNTIME_USER" \
  --file /verify/verify-schema.sql
printf 'database initialization and schema verification passed\n'
