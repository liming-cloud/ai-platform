-- Authoritative fail-closed classification for objects exposed to the backend
-- database role. Both role provisioning and schema verification include this
-- file so an object cannot be granted through a broader, divergent path.

CREATE TEMPORARY TABLE aip_runtime_table_policy (
  object_name NAME PRIMARY KEY,
  access_profile TEXT NOT NULL
    CHECK (access_profile IN ('business_dml', 'append_only', 'control_plane'))
);

INSERT INTO aip_runtime_table_policy(object_name, access_profile) VALUES
  ('iam_tenant', 'business_dml'),
  ('iam_department', 'business_dml'),
  ('iam_user', 'business_dml'),
  ('iam_role', 'business_dml'),
  ('iam_permission', 'business_dml'),
  ('iam_menu', 'business_dml'),
  ('iam_role_menu', 'business_dml'),
  ('iam_tenant_feature', 'business_dml'),
  ('iam_menu_feature', 'business_dml'),
  ('iam_user_role', 'business_dml'),
  ('iam_role_permission', 'business_dml'),
  ('iam_resource_acl', 'business_dml'),
  ('iam_permission_request', 'business_dml'),
  ('iam_delegated_tenant_action', 'business_dml'),
  ('iam_user_invitation', 'business_dml'),
  ('iam_notification_outbox', 'business_dml'),
  ('mdl_provider', 'business_dml'),
  ('mdl_config', 'business_dml'),
  ('mdl_secret', 'business_dml'),
  ('mdl_invocation_log', 'business_dml'),
  ('mdl_creation_request', 'business_dml'),
  ('mdl_model_usage_policy_outbox', 'business_dml'),
  ('mdl_approval_request_outbox', 'business_dml'),
  ('mdl_approval_decision_inbox', 'business_dml'),
  ('kb_knowledge_base', 'business_dml'),
  ('kb_document', 'business_dml'),
  ('kb_document_chunk', 'business_dml'),
  ('kb_embedding_job', 'business_dml'),
  ('kb_document_chunk_embedding', 'business_dml'),
  ('kb_knowledge_index_active', 'business_dml'),
  ('kb_knowledge_index_desired', 'business_dml'),
  ('kb_knowledge_index_rebuild', 'business_dml'),
  ('kb_knowledge_index_rebuild_member', 'business_dml'),
  ('kb_document_object_cleanup', 'business_dml'),
  ('kb_model_usage_policy_projection', 'business_dml'),
  ('kb_model_usage_policy_event_receipt', 'business_dml'),
  ('kb_citation_record', 'business_dml'),
  ('app_application', 'business_dml'),
  ('app_bound_knowledge_base', 'business_dml'),
  ('app_chat_session', 'business_dml'),
  ('app_chat_message', 'business_dml'),
  ('app_chat_run', 'business_dml'),
  ('app_chat_run_replay_request', 'business_dml'),
  ('app_chat_retrieval_evidence', 'business_dml'),
  ('app_chat_message_feedback', 'business_dml'),
  ('app_model_usage_policy_projection', 'business_dml'),
  ('app_model_usage_policy_event_receipt', 'business_dml'),
  ('app_publish_preflight_result', 'business_dml'),
  ('app_application_release', 'business_dml'),
  ('app_application_active_release', 'business_dml'),
  ('app_application_release_outbox', 'business_dml'),
  ('appr_task', 'business_dml'),
  ('appr_record', 'business_dml'),
  ('appr_decision_outbox', 'business_dml'),
  ('appr_model_approval_cancellation', 'business_dml'),
  ('audit_operation_log', 'append_only'),
  ('audit_security_log', 'append_only'),
  ('rag_authorization_decision_log', 'append_only'),
  ('summary_metric_snapshot', 'business_dml');

-- Sequences owned by a classified table inherit that table's profile. Only
-- standalone sequences need an explicit entry here.
CREATE TEMPORARY TABLE aip_runtime_standalone_sequence_policy (
  object_name NAME PRIMARY KEY,
  access_profile TEXT NOT NULL
    CHECK (access_profile IN ('business_sequence', 'control_plane'))
);

INSERT INTO aip_runtime_standalone_sequence_policy(object_name, access_profile) VALUES
  ('mdl_model_usage_policy_revision_seq', 'business_sequence');

CREATE TEMPORARY TABLE aip_runtime_function_policy (
  object_identity TEXT PRIMARY KEY,
  access_profile TEXT NOT NULL
    CHECK (access_profile IN ('runtime_execute', 'trigger_only'))
);

INSERT INTO aip_runtime_function_policy(object_identity, access_profile) VALUES
  ('app_chat_run_finalization_counter_guard()', 'trigger_only'),
  ('mdl_model_runtime_options_digest(jsonb)', 'runtime_execute'),
  ('mdl_model_usage_policy_digest(jsonb)', 'runtime_execute'),
  ('reject_app_application_release_mutation()', 'trigger_only');
