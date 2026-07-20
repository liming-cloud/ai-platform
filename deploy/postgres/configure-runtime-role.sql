\set ON_ERROR_STOP on

-- The schema owner initializes database objects. The backend receives a
-- separate, non-privileged login with only the classified application access.
BEGIN;

CREATE TEMPORARY TABLE aip_runtime_role_configuration (
  role_name TEXT NOT NULL,
  role_password TEXT NOT NULL
) ON COMMIT DROP;

INSERT INTO aip_runtime_role_configuration(role_name, role_password)
VALUES (:'aip_runtime_role', :'aip_runtime_password');

\i /runtime-role/runtime-object-policy.sql

DO $$
DECLARE
  runtime_role TEXT;
BEGIN
  SELECT role_name INTO STRICT runtime_role
  FROM aip_runtime_role_configuration;

  IF runtime_role::TEXT = current_user THEN
    RAISE EXCEPTION 'runtime database role must differ from the schema owner role';
  END IF;
  IF length(runtime_role::TEXT) > 63 THEN
    RAISE EXCEPTION 'runtime database role exceeds the PostgreSQL identifier limit';
  END IF;
  IF runtime_role::TEXT !~ '^[A-Za-z_][A-Za-z0-9_]*$' THEN
    RAISE EXCEPTION 'runtime database role must be a portable PostgreSQL identifier';
  END IF;
END;
$$;

DO $$
DECLARE
  unclassified_relations TEXT;
  unclassified_sequences TEXT;
  unclassified_routines TEXT;
  unsafe_security_definers TEXT;
BEGIN
  SELECT string_agg(
           format('%I (%s)', relation.relname, relation.relkind),
           ', ' ORDER BY relation.relname
         )
    INTO unclassified_relations
    FROM pg_class AS relation
    JOIN pg_namespace AS schema_name ON schema_name.oid = relation.relnamespace
    LEFT JOIN aip_runtime_table_policy AS policy
      ON policy.object_name = relation.relname
   WHERE schema_name.nspname = 'public'
     AND relation.relkind IN ('r', 'p', 'v', 'm', 'f')
     AND policy.object_name IS NULL;

  IF unclassified_relations IS NOT NULL THEN
    RAISE EXCEPTION 'unclassified public relations: %', unclassified_relations;
  END IF;

  SELECT string_agg(sequence.relname, ', ' ORDER BY sequence.relname)
    INTO unclassified_sequences
    FROM pg_class AS sequence
    JOIN pg_namespace AS schema_name ON schema_name.oid = sequence.relnamespace
    LEFT JOIN pg_depend AS ownership
      ON ownership.classid = 'pg_class'::regclass
     AND ownership.objid = sequence.oid
     AND ownership.refclassid = 'pg_class'::regclass
     AND ownership.deptype IN ('a', 'i')
    LEFT JOIN pg_class AS owner_relation ON owner_relation.oid = ownership.refobjid
    LEFT JOIN pg_namespace AS owner_schema ON owner_schema.oid = owner_relation.relnamespace
    LEFT JOIN aip_runtime_table_policy AS owner_policy
      ON owner_schema.nspname = 'public'
     AND owner_policy.object_name = owner_relation.relname
    LEFT JOIN aip_runtime_standalone_sequence_policy AS standalone_policy
      ON standalone_policy.object_name = sequence.relname
   WHERE schema_name.nspname = 'public'
     AND sequence.relkind = 'S'
     AND owner_policy.object_name IS NULL
     AND standalone_policy.object_name IS NULL;

  IF unclassified_sequences IS NOT NULL THEN
    RAISE EXCEPTION 'unclassified public sequences: %', unclassified_sequences;
  END IF;

  SELECT string_agg(
           routine.proname || '(' || oidvectortypes(routine.proargtypes) || ')',
           ', ' ORDER BY routine.proname, oidvectortypes(routine.proargtypes)
         )
    INTO unclassified_routines
    FROM pg_proc AS routine
    JOIN pg_namespace AS schema_name ON schema_name.oid = routine.pronamespace
    LEFT JOIN aip_runtime_function_policy AS policy
      ON policy.object_identity =
         routine.proname || '(' || oidvectortypes(routine.proargtypes) || ')'
   WHERE schema_name.nspname = 'public'
     AND policy.object_identity IS NULL
     AND NOT EXISTS (
       SELECT 1
       FROM pg_depend AS extension_dependency
       JOIN pg_extension AS extension
         ON extension.oid = extension_dependency.refobjid
      WHERE extension_dependency.classid = 'pg_proc'::regclass
        AND extension_dependency.objid = routine.oid
        AND extension_dependency.refclassid = 'pg_extension'::regclass
        AND extension_dependency.deptype = 'e'
        AND extension.extname = 'vector'
     );

  IF unclassified_routines IS NOT NULL THEN
    RAISE EXCEPTION 'unclassified public routines: %', unclassified_routines;
  END IF;

  SELECT string_agg(
           routine.proname || '(' || oidvectortypes(routine.proargtypes) || ')',
           ', ' ORDER BY routine.proname, oidvectortypes(routine.proargtypes)
         )
    INTO unsafe_security_definers
    FROM pg_proc AS routine
    JOIN pg_namespace AS schema_name ON schema_name.oid = routine.pronamespace
   WHERE schema_name.nspname = 'public'
     AND routine.prosecdef;

  IF unsafe_security_definers IS NOT NULL THEN
    RAISE EXCEPTION 'SECURITY DEFINER routines are forbidden in the runtime schema: %',
      unsafe_security_definers;
  END IF;
END;
$$;

SELECT format('CREATE ROLE %I', configuration.role_name)
FROM aip_runtime_role_configuration AS configuration
WHERE NOT EXISTS (
  SELECT 1 FROM pg_roles WHERE rolname = configuration.role_name
)
\gexec

SELECT format(
  'ALTER ROLE %I WITH LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE '
  'NOINHERIT NOREPLICATION NOBYPASSRLS',
  configuration.role_name,
  configuration.role_password
)
FROM aip_runtime_role_configuration AS configuration
\gexec

-- Remove any membership or stale privileges left by an earlier local deploy.
SELECT format('REVOKE %I FROM %I', granted_role.rolname, configuration.role_name)
FROM aip_runtime_role_configuration AS configuration
JOIN pg_roles AS runtime_role ON runtime_role.rolname = configuration.role_name
JOIN pg_auth_members AS membership ON membership.member = runtime_role.oid
JOIN pg_roles AS granted_role ON granted_role.oid = membership.roleid
\gexec

SELECT format(
  'REVOKE ALL PRIVILEGES ON DATABASE %I FROM %I',
  current_database(),
  configuration.role_name
)
FROM aip_runtime_role_configuration AS configuration
\gexec

SELECT format(
  'REVOKE CREATE, TEMPORARY ON DATABASE %I FROM PUBLIC',
  current_database()
)
\gexec

SELECT format('REVOKE ALL PRIVILEGES ON SCHEMA public FROM %I', role_name)
FROM aip_runtime_role_configuration
\gexec
SELECT format('REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA public FROM %I', role_name)
FROM aip_runtime_role_configuration
\gexec
SELECT format('REVOKE ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public FROM %I', role_name)
FROM aip_runtime_role_configuration
\gexec
SELECT format('REVOKE ALL PRIVILEGES ON ALL ROUTINES IN SCHEMA public FROM %I', role_name)
FROM aip_runtime_role_configuration
\gexec

-- Public cannot be an indirect grant path. The runtime receives only the
-- objects classified below, and future objects remain inaccessible by default.
REVOKE ALL PRIVILEGES ON SCHEMA public FROM PUBLIC;
REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA public FROM PUBLIC;
REVOKE ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public FROM PUBLIC;
REVOKE ALL PRIVILEGES ON ALL ROUTINES IN SCHEMA public FROM PUBLIC;

SELECT format('GRANT CONNECT ON DATABASE %I TO %I', current_database(), role_name)
FROM aip_runtime_role_configuration
\gexec
SELECT format('GRANT USAGE ON SCHEMA public TO %I', role_name)
FROM aip_runtime_role_configuration
\gexec
SELECT format(
  'GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.%I TO %I',
  policy.object_name,
  configuration.role_name
)
FROM aip_runtime_role_configuration AS configuration
JOIN aip_runtime_table_policy AS policy ON policy.access_profile = 'business_dml'
JOIN pg_class AS relation ON relation.relname = policy.object_name
JOIN pg_namespace AS schema_name
  ON schema_name.oid = relation.relnamespace
 AND schema_name.nspname = 'public'
WHERE relation.relkind IN ('r', 'p', 'v', 'm', 'f')
\gexec
SELECT format(
  'GRANT SELECT, INSERT ON TABLE public.%I TO %I',
  policy.object_name,
  configuration.role_name
)
FROM aip_runtime_role_configuration AS configuration
JOIN aip_runtime_table_policy AS policy ON policy.access_profile = 'append_only'
JOIN pg_class AS relation ON relation.relname = policy.object_name
JOIN pg_namespace AS schema_name
  ON schema_name.oid = relation.relnamespace
 AND schema_name.nspname = 'public'
WHERE relation.relkind IN ('r', 'p', 'v', 'm', 'f')
\gexec
SELECT DISTINCT format(
  'GRANT %s ON SEQUENCE public.%I TO %I',
  CASE
    WHEN owner_policy.access_profile = 'append_only' THEN 'USAGE'
    ELSE 'USAGE, SELECT'
  END,
  sequence.relname,
  configuration.role_name
)
FROM aip_runtime_role_configuration AS configuration
JOIN pg_class AS sequence ON sequence.relkind = 'S'
JOIN pg_namespace AS sequence_schema
  ON sequence_schema.oid = sequence.relnamespace
 AND sequence_schema.nspname = 'public'
LEFT JOIN pg_depend AS ownership
  ON ownership.classid = 'pg_class'::regclass
 AND ownership.objid = sequence.oid
 AND ownership.refclassid = 'pg_class'::regclass
 AND ownership.deptype IN ('a', 'i')
LEFT JOIN pg_class AS owner_relation ON owner_relation.oid = ownership.refobjid
LEFT JOIN pg_namespace AS owner_schema ON owner_schema.oid = owner_relation.relnamespace
LEFT JOIN aip_runtime_table_policy AS owner_policy
  ON owner_schema.nspname = 'public'
 AND owner_policy.object_name = owner_relation.relname
LEFT JOIN aip_runtime_standalone_sequence_policy AS standalone_policy
  ON standalone_policy.object_name = sequence.relname
WHERE owner_policy.access_profile IN ('business_dml', 'append_only')
   OR standalone_policy.access_profile = 'business_sequence'
\gexec
SELECT format(
  'GRANT EXECUTE ON %s public.%I(%s) TO %I',
  CASE WHEN routine.prokind = 'p' THEN 'PROCEDURE' ELSE 'FUNCTION' END,
  routine.proname,
  oidvectortypes(routine.proargtypes),
  configuration.role_name
)
FROM aip_runtime_role_configuration AS configuration
JOIN pg_proc AS routine ON TRUE
JOIN pg_namespace AS schema_name
  ON schema_name.oid = routine.pronamespace
 AND schema_name.nspname = 'public'
LEFT JOIN aip_runtime_function_policy AS policy
  ON policy.object_identity =
     routine.proname || '(' || oidvectortypes(routine.proargtypes) || ')'
WHERE policy.access_profile = 'runtime_execute'
   OR EXISTS (
     SELECT 1
     FROM pg_depend AS extension_dependency
     JOIN pg_extension AS extension ON extension.oid = extension_dependency.refobjid
    WHERE extension_dependency.classid = 'pg_proc'::regclass
      AND extension_dependency.objid = routine.oid
      AND extension_dependency.refclassid = 'pg_extension'::regclass
      AND extension_dependency.deptype = 'e'
      AND extension.extname = 'vector'
   )
\gexec

-- Schema-owner objects retain a fail-closed default privilege policy.
SELECT format(
  'ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public '
  'REVOKE ALL PRIVILEGES ON TABLES FROM PUBLIC',
  current_user
)
\gexec
SELECT format(
  'ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public '
  'REVOKE ALL PRIVILEGES ON TABLES FROM %I',
  current_user,
  role_name
)
FROM aip_runtime_role_configuration
\gexec
SELECT format(
  'ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public '
  'REVOKE ALL PRIVILEGES ON SEQUENCES FROM PUBLIC',
  current_user
)
\gexec
SELECT format(
  'ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public '
  'REVOKE ALL PRIVILEGES ON SEQUENCES FROM %I',
  current_user,
  role_name
)
FROM aip_runtime_role_configuration
\gexec
SELECT format(
  'ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public '
  'REVOKE ALL PRIVILEGES ON ROUTINES FROM PUBLIC',
  current_user
)
\gexec
SELECT format(
  'ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public '
  'REVOKE ALL PRIVILEGES ON ROUTINES FROM %I',
  current_user,
  role_name
)
FROM aip_runtime_role_configuration
\gexec

SELECT format(
  'ALTER ROLE %I SET search_path TO public, pg_catalog',
  role_name
)
FROM aip_runtime_role_configuration
\gexec

COMMIT;
