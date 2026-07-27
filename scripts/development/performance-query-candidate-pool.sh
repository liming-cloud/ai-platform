#!/usr/bin/env bash
set -euo pipefail

readonly REPOSITORY_ROOT="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/../.." >/dev/null 2>&1
  pwd
)"
readonly COMPOSE_FILE="${REPOSITORY_ROOT}/deploy/compose/compose.yml"
readonly ENV_FILE="${REPOSITORY_ROOT}/deploy/compose/.env.example"
readonly P95_LIMIT_SECONDS="${FOUNDATION_QUERY_POOL_P95_SECONDS:-0.200}"

compose() {
  docker compose \
    --env-file "${ENV_FILE}" \
    -f "${COMPOSE_FILE}" \
    "$@"
}

metrics="$(
  compose exec -T postgres \
    psql \
    -q \
    -v ON_ERROR_STOP=1 \
    -U foundation_app \
    -d foundation_v1 \
    -At \
    -F '|' <<'SQL'
CREATE TEMP TABLE query_candidate_pool_duration (
    duration_seconds DOUBLE PRECISION NOT NULL
);

DO $performance$
DECLARE
    task_tenant BIGINT := 100000000000000001;
    task_space UUID;
    task_index UUID;
    task_deployment UUID;
    task_revision BIGINT;
    task_dimension INTEGER;
    task_embedding vector;
    task_started TIMESTAMPTZ;
BEGIN
    SELECT
        version.knowledge_space_id,
        version.index_version_id,
        version.embedding_deployment_id,
        version.embedding_deployment_revision,
        version.embedding_dimension,
        chunk.embedding
    INTO
        task_space,
        task_index,
        task_deployment,
        task_revision,
        task_dimension,
        task_embedding
    FROM assistant_delivery.assistant_version version
    JOIN knowledge_data.index_chunk chunk
      ON chunk.tenant_id = version.tenant_id
     AND chunk.knowledge_space_id = version.knowledge_space_id
     AND chunk.index_version_id = version.index_version_id
     AND chunk.embedding_deployment_id = version.embedding_deployment_id
     AND chunk.embedding_deployment_revision =
         version.embedding_deployment_revision
     AND chunk.embedding_dimension = version.embedding_dimension
    WHERE version.tenant_id = task_tenant
      AND version.status = 'PUBLISHED'
      AND version.minimum_score = 0
    ORDER BY version.published_at DESC, version.id DESC, chunk.chunk_id
    LIMIT 1;

    IF task_embedding IS NULL THEN
        RAISE EXCEPTION '没有可用于候选池性能测试的冻结助手索引';
    END IF;

    FOR task_iteration IN 1..120 LOOP
        task_started := clock_timestamp();
        PERFORM chunk.chunk_id
        FROM knowledge_data.index_chunk chunk
        WHERE chunk.tenant_id = task_tenant
          AND chunk.knowledge_space_id = task_space
          AND chunk.index_version_id = task_index
          AND chunk.embedding_deployment_id = task_deployment
          AND chunk.embedding_deployment_revision = task_revision
          AND chunk.embedding_dimension = task_dimension
        ORDER BY chunk.embedding <=> task_embedding, chunk.chunk_id
        LIMIT 20;
        IF task_iteration > 20 THEN
            INSERT INTO query_candidate_pool_duration(duration_seconds)
            VALUES (
                EXTRACT(
                    EPOCH FROM clock_timestamp() - task_started
                )
            );
        END IF;
    END LOOP;
END
$performance$;

SELECT
    percentile_cont(0.5) WITHIN GROUP (ORDER BY duration_seconds),
    percentile_cont(0.95) WITHIN GROUP (ORDER BY duration_seconds),
    max(duration_seconds)
FROM query_candidate_pool_duration;
SQL
)"

IFS='|' read -r p50 p95 maximum <<<"${metrics}"
if ! awk -v actual="${p95}" -v limit="${P95_LIMIT_SECONDS}" \
  'BEGIN { exit !(actual <= limit) }'; then
  echo "二十条候选池 95 分位 ${p95} 秒超过门禁 ${P95_LIMIT_SECONDS} 秒" >&2
  exit 1
fi

echo "二十条候选池性能通过：预热 20 次，采样 100 次"
echo "中位数 ${p50} 秒，95 分位 ${p95} 秒，最大值 ${maximum} 秒"
