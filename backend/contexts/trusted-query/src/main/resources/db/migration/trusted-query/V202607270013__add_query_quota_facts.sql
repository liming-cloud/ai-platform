-- 所属上下文：可信查询。
-- 目的：冻结查询接受时的 UTC 每日限额、占用序号和运行租约，并支持原子配额判断。
-- 影响：历史查询使用最大整数表示迁移前未受当前演示限额约束；历史运行任务租约立即过期。
-- 回滚限制：新增事实被运营接口读取后不能安全删除，只能通过后续迁移修正。

ALTER TABLE trusted_query.query_run
    ADD COLUMN quota_period_start TIMESTAMPTZ,
    ADD COLUMN quota_period_end TIMESTAMPTZ,
    ADD COLUMN daily_query_limit INTEGER,
    ADD COLUMN daily_query_ordinal INTEGER,
    ADD COLUMN concurrent_query_limit INTEGER,
    ADD COLUMN accepted_lease_until TIMESTAMPTZ,
    ADD COLUMN running_lease_until TIMESTAMPTZ;

WITH historical AS (
    SELECT tenant_id,
           id,
           row_number() OVER (
               PARTITION BY tenant_id, date_trunc('day', created_at AT TIME ZONE 'UTC')
               ORDER BY created_at, id
           )::INTEGER AS daily_ordinal
    FROM trusted_query.query_run
)
UPDATE trusted_query.query_run AS query_run
SET quota_period_start =
        date_trunc('day', query_run.created_at AT TIME ZONE 'UTC') AT TIME ZONE 'UTC',
    quota_period_end =
        (date_trunc('day', query_run.created_at AT TIME ZONE 'UTC') + INTERVAL '1 day')
            AT TIME ZONE 'UTC',
    daily_query_limit = 2147483647,
    daily_query_ordinal = historical.daily_ordinal,
    concurrent_query_limit = 2147483647,
    accepted_lease_until = query_run.created_at,
    running_lease_until = CASE
        WHEN query_run.status = 'RUNNING' THEN query_run.created_at
        ELSE NULL
    END
FROM historical
WHERE historical.tenant_id = query_run.tenant_id
  AND historical.id = query_run.id;

ALTER TABLE trusted_query.query_run
    ALTER COLUMN quota_period_start SET NOT NULL,
    ALTER COLUMN quota_period_end SET NOT NULL,
    ALTER COLUMN daily_query_limit SET NOT NULL,
    ALTER COLUMN daily_query_ordinal SET NOT NULL,
    ALTER COLUMN concurrent_query_limit SET NOT NULL,
    ALTER COLUMN accepted_lease_until SET NOT NULL;

ALTER TABLE trusted_query.query_run
    ADD CONSTRAINT ck_query_run_quota_period
        CHECK (quota_period_end > quota_period_start),
    ADD CONSTRAINT ck_query_run_daily_quota
        CHECK (
            daily_query_limit > 0
            AND daily_query_ordinal > 0
            AND daily_query_ordinal <= daily_query_limit
        ),
    ADD CONSTRAINT ck_query_run_concurrent_quota
        CHECK (concurrent_query_limit > 0),
    ADD CONSTRAINT ck_query_run_running_lease
        CHECK (
            (
                status = 'RUNNING'
                AND running_lease_until IS NOT NULL
            )
            OR (
                status <> 'RUNNING'
                AND running_lease_until IS NULL
            )
        );

CREATE INDEX ix_query_run_active_lease
    ON trusted_query.query_run (tenant_id, running_lease_until)
    WHERE status = 'RUNNING';
