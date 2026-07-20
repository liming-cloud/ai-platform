\set ON_ERROR_STOP on

CREATE TEMPORARY TABLE aip_expected_runtime_role (
  role_name TEXT PRIMARY KEY
);
INSERT INTO aip_expected_runtime_role(role_name) VALUES (:'aip_runtime_role');

\i /runtime-role/runtime-object-policy.sql

DO $$
DECLARE
  runtime_role TEXT;
  missing_relations TEXT;
  missing_sequences TEXT;
  missing_routines TEXT;
BEGIN
  SELECT role_name INTO STRICT runtime_role
  FROM aip_expected_runtime_role;

  IF NOT EXISTS (
    SELECT 1
    FROM pg_extension
    WHERE extname = 'vector'
  ) THEN
    RAISE EXCEPTION 'required vector extension is not installed';
  END IF;

  SELECT string_agg(policy.object_name, ', ' ORDER BY policy.object_name)
    INTO missing_relations
    FROM aip_runtime_table_policy AS policy
    LEFT JOIN pg_class AS relation
      ON relation.relname = policy.object_name
    LEFT JOIN pg_namespace AS schema_name
      ON schema_name.oid = relation.relnamespace
     AND schema_name.nspname = 'public'
   WHERE schema_name.oid IS NULL
      OR relation.relkind NOT IN ('r', 'p', 'v', 'm', 'f');

  IF missing_relations IS NOT NULL THEN
    RAISE EXCEPTION 'missing required relations: %', missing_relations;
  END IF;

  SELECT string_agg(policy.object_name, ', ' ORDER BY policy.object_name)
    INTO missing_sequences
    FROM aip_runtime_standalone_sequence_policy AS policy
    LEFT JOIN pg_class AS sequence
      ON sequence.relname = policy.object_name
     AND sequence.relkind = 'S'
    LEFT JOIN pg_namespace AS schema_name
      ON schema_name.oid = sequence.relnamespace
     AND schema_name.nspname = 'public'
   WHERE schema_name.oid IS NULL;

  IF missing_sequences IS NOT NULL THEN
    RAISE EXCEPTION 'missing required sequences: %', missing_sequences;
  END IF;

  SELECT string_agg(policy.object_identity, ', ' ORDER BY policy.object_identity)
    INTO missing_routines
    FROM aip_runtime_function_policy AS policy
    LEFT JOIN pg_proc AS routine
      ON policy.object_identity =
         routine.proname || '(' || oidvectortypes(routine.proargtypes) || ')'
    LEFT JOIN pg_namespace AS schema_name
      ON schema_name.oid = routine.pronamespace
     AND schema_name.nspname = 'public'
   WHERE schema_name.oid IS NULL;

  IF missing_routines IS NOT NULL THEN
    RAISE EXCEPTION 'missing required routines: %', missing_routines;
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM iam_tenant
    WHERE id = 2001
      AND tenant_code = 'platform'
      AND status = 'ACTIVE'
  ) OR NOT EXISTS (
    SELECT 1
    FROM iam_user
    WHERE id = 1001
      AND tenant_id = 2001
      AND username = 'admin'
      AND status = 'ACTIVE'
  ) OR NOT EXISTS (
    SELECT 1
    FROM iam_role
    WHERE id = 3001
      AND tenant_id = 2001
      AND role_code = 'platform-admin'
      AND status = 'ACTIVE'
  ) THEN
    RAISE EXCEPTION 'base platform administrator data is incomplete';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM pg_roles
    WHERE rolname = runtime_role
      AND rolcanlogin
      AND NOT rolsuper
      AND NOT rolcreatedb
      AND NOT rolcreaterole
  ) THEN
    RAISE EXCEPTION 'runtime database role is missing or over-privileged';
  END IF;

  IF NOT has_schema_privilege(runtime_role, 'public', 'USAGE')
     OR has_schema_privilege(runtime_role, 'public', 'CREATE')
     OR NOT has_table_privilege(runtime_role, 'public.iam_tenant', 'SELECT')
     OR has_table_privilege(runtime_role, 'public.audit_operation_log', 'UPDATE')
     OR has_table_privilege(runtime_role, 'public.audit_operation_log', 'DELETE') THEN
    RAISE EXCEPTION 'runtime database privileges do not match the deployment policy';
  END IF;
END;
$$;
