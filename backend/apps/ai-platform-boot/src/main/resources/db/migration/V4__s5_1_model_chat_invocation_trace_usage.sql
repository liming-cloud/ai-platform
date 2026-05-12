ALTER TABLE model_invocation_record
    ADD COLUMN IF NOT EXISTS invocation_kind VARCHAR(32) NOT NULL DEFAULT 'PROVIDER_TEST';

ALTER TABLE model_invocation_record
    ADD COLUMN IF NOT EXISTS trace_id VARCHAR(96);

ALTER TABLE model_invocation_record
    ADD COLUMN IF NOT EXISTS input_chars INTEGER;

ALTER TABLE model_invocation_record
    ADD COLUMN IF NOT EXISTS output_chars INTEGER;

ALTER TABLE model_invocation_record
    ADD COLUMN IF NOT EXISTS cost_usd NUMERIC(12, 6);

ALTER TABLE model_invocation_record
    ADD COLUMN IF NOT EXISTS cost_missing_reason VARCHAR(256);

CREATE INDEX IF NOT EXISTS idx_model_invocation_tenant_trace
    ON model_invocation_record (tenant_id, trace_id);

CREATE INDEX IF NOT EXISTS idx_model_invocation_tenant_kind_created
    ON model_invocation_record (tenant_id, invocation_kind, created_at DESC);
