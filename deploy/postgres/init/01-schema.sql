--
-- PostgreSQL database dump
--


-- Dumped from database version 16.14 (Debian 16.14-1.pgdg12+1)
-- Dumped by pg_dump version 16.14 (Debian 16.14-1.pgdg12+1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: public; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA public;


--
-- Name: SCHEMA public; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON SCHEMA public IS 'standard public schema';

-- Required by knowledge-base vector columns and indexes.
CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public;


--
-- Name: app_chat_run_finalization_counter_guard(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.app_chat_run_finalization_counter_guard() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.phase = 'RESULT_RECEIVED' AND NEW.finalization_json IS NOT NULL THEN
        IF OLD.phase IS DISTINCT FROM 'RESULT_RECEIVED' OR OLD.finalization_json IS NULL THEN
            IF NEW.finalization_attempt_count = OLD.finalization_attempt_count THEN
                NEW.finalization_attempt_count := GREATEST(OLD.finalization_attempt_count, 1);
            END IF;
            IF NEW.finalization_cycle_attempt_count = OLD.finalization_cycle_attempt_count THEN
                NEW.finalization_cycle_attempt_count := GREATEST(OLD.finalization_cycle_attempt_count, 1);
            END IF;
        ELSIF NEW.fencing_token > OLD.fencing_token THEN
            IF NEW.finalization_attempt_count = OLD.finalization_attempt_count THEN
                NEW.finalization_attempt_count := OLD.finalization_attempt_count + 1;
            END IF;
            IF NEW.finalization_cycle_attempt_count = OLD.finalization_cycle_attempt_count THEN
                NEW.finalization_cycle_attempt_count := OLD.finalization_cycle_attempt_count + 1;
            END IF;
        END IF;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: mdl_model_runtime_options_digest(jsonb); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.mdl_model_runtime_options_digest(effective_options jsonb) RETURNS character varying
    LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
    AS $$
  SELECT encode(sha256(convert_to(effective_options::text, 'UTF8')), 'hex')
$$;


--
-- Name: mdl_model_usage_policy_digest(jsonb); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.mdl_model_usage_policy_digest(payload jsonb) RETURNS character varying
    LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
    AS $$
  SELECT CASE
    WHEN jsonb_typeof(payload) = 'object' THEN encode(sha256(convert_to(
      jsonb_set(payload, '{policyDigest}', '""'::jsonb, true)::text,
      'UTF8'
    )), 'hex')
    ELSE NULL
  END
$$;


--
-- Name: reject_app_application_release_mutation(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.reject_app_application_release_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  RAISE EXCEPTION 'app_application_release is immutable';
END;
$$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: app_application; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.app_application (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    app_code character varying(64) NOT NULL,
    app_name character varying(128) NOT NULL,
    app_type character varying(32) NOT NULL,
    description text,
    default_model_config_id bigint,
    system_prompt text,
    status character varying(32) NOT NULL,
    published_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by bigint,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_by bigint,
    deleted_at timestamp with time zone,
    version integer DEFAULT 0 NOT NULL
);


--
-- Name: app_application_active_release; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.app_application_active_release (
    tenant_id bigint NOT NULL,
    application_id bigint NOT NULL,
    active_release_id bigint NOT NULL,
    activated_by bigint NOT NULL,
    activated_at timestamp with time zone NOT NULL
);


--
-- Name: TABLE app_application_active_release; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.app_application_active_release IS '应用运行时唯一 active release 指针';


--
-- Name: app_application_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.app_application_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: app_application_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.app_application_id_seq OWNED BY public.app_application.id;


--
-- Name: app_application_release; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.app_application_release (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    application_id bigint NOT NULL,
    version_no bigint NOT NULL,
    source_release_id bigint,
    request_id character varying(128) NOT NULL,
    operation character varying(32) NOT NULL,
    application_type character varying(32) NOT NULL,
    application_name character varying(128) NOT NULL,
    description text NOT NULL,
    model_config_id bigint NOT NULL,
    system_prompt text NOT NULL,
    knowledge_base_ids_json jsonb NOT NULL,
    tool_ids_json jsonb NOT NULL,
    owner_user_ids_json jsonb NOT NULL,
    allowed_user_ids_json jsonb NOT NULL,
    application_created_at timestamp with time zone NOT NULL,
    published_by bigint NOT NULL,
    reason text NOT NULL,
    trace_id character varying(128) NOT NULL,
    published_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_app_application_release_json CHECK (((jsonb_typeof(knowledge_base_ids_json) = 'array'::text) AND (jsonb_typeof(tool_ids_json) = 'array'::text) AND (jsonb_typeof(owner_user_ids_json) = 'array'::text) AND (jsonb_typeof(allowed_user_ids_json) = 'array'::text))),
    CONSTRAINT ck_app_application_release_operation CHECK (((((operation)::text = 'PUBLISH'::text) AND (source_release_id IS NULL)) OR (((operation)::text = 'ROLLBACK'::text) AND (source_release_id IS NOT NULL)))),
    CONSTRAINT ck_app_application_release_reason CHECK ((length(btrim(reason)) > 0)),
    CONSTRAINT ck_app_application_release_trace CHECK ((length(btrim((trace_id)::text)) > 0)),
    CONSTRAINT ck_app_application_release_version CHECK ((version_no > 0))
);


--
-- Name: TABLE app_application_release; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.app_application_release IS '租户隔离且插入后不可修改的应用发布快照；回滚通过复制历史快照创建新版本';


--
-- Name: app_application_release_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.app_application_release_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: app_application_release_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.app_application_release_id_seq OWNED BY public.app_application_release.id;


--
-- Name: app_application_release_outbox; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.app_application_release_outbox (
    id bigint NOT NULL,
    event_id character varying(255) NOT NULL,
    event_type character varying(128) NOT NULL,
    tenant_id bigint NOT NULL,
    aggregate_type character varying(128) NOT NULL,
    aggregate_id character varying(128) NOT NULL,
    event_version bigint NOT NULL,
    schema_version character varying(32) NOT NULL,
    occurred_at timestamp with time zone NOT NULL,
    trace_id character varying(128) NOT NULL,
    producer character varying(128) NOT NULL,
    payload_json jsonb NOT NULL,
    status character varying(32) DEFAULT 'PENDING'::character varying NOT NULL,
    attempts integer DEFAULT 0 NOT NULL,
    next_attempt_at timestamp with time zone DEFAULT now() NOT NULL,
    lease_owner character varying(128),
    lease_until timestamp with time zone,
    published_at timestamp with time zone,
    last_error character varying(128),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_app_application_release_outbox_attempts CHECK ((attempts >= 0)),
    CONSTRAINT ck_app_application_release_outbox_lease CHECK (((((status)::text = 'IN_FLIGHT'::text) AND (lease_owner IS NOT NULL) AND (lease_until IS NOT NULL)) OR (((status)::text <> 'IN_FLIGHT'::text) AND (lease_owner IS NULL) AND (lease_until IS NULL)))),
    CONSTRAINT ck_app_application_release_outbox_protocol CHECK ((((event_type)::text = ANY ((ARRAY['ApplicationReleasePublished'::character varying, 'ApplicationReleaseRolledBack'::character varying])::text[])) AND ((aggregate_type)::text = 'AiApplication'::text) AND (event_version > 0) AND ((schema_version)::text = 'v1'::text) AND ((producer)::text = 'app-studio'::text))),
    CONSTRAINT ck_app_application_release_outbox_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'IN_FLIGHT'::character varying, 'PUBLISHED'::character varying])::text[])))
);


--
-- Name: TABLE app_application_release_outbox; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.app_application_release_outbox IS '应用发布和回滚事件的事务 outbox';


--
-- Name: app_application_release_outbox_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.app_application_release_outbox_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: app_application_release_outbox_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.app_application_release_outbox_id_seq OWNED BY public.app_application_release_outbox.id;


--
-- Name: app_bound_knowledge_base; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.app_bound_knowledge_base (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    application_id bigint NOT NULL,
    knowledge_base_id bigint NOT NULL,
    retrieval_top_k integer,
    score_threshold numeric(6,5),
    status character varying(32) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by bigint,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_by bigint,
    deleted_at timestamp with time zone,
    version integer DEFAULT 0 NOT NULL
);


--
-- Name: app_bound_knowledge_base_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.app_bound_knowledge_base_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: app_bound_knowledge_base_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.app_bound_knowledge_base_id_seq OWNED BY public.app_bound_knowledge_base.id;


--
-- Name: app_chat_message; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.app_chat_message (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    session_id bigint NOT NULL,
    application_id bigint NOT NULL,
    role character varying(32) NOT NULL,
    content text NOT NULL,
    content_format character varying(32) DEFAULT 'markdown'::character varying NOT NULL,
    model_invocation_log_id bigint,
    token_count integer DEFAULT 0 NOT NULL,
    citation_count integer DEFAULT 0 NOT NULL,
    trace_id character varying(64),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by bigint,
    client_message_id character varying(128)
);


--
-- Name: app_chat_message_feedback; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.app_chat_message_feedback (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    feedback_id character varying(64) NOT NULL,
    session_id bigint NOT NULL,
    assistant_message_id bigint NOT NULL,
    user_id bigint NOT NULL,
    rating character varying(32) NOT NULL,
    labels_json jsonb DEFAULT '[]'::jsonb NOT NULL,
    comment character varying(1000),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT app_chat_message_feedback_rating_check CHECK (((rating)::text = ANY ((ARRAY['HELPFUL'::character varying, 'UNHELPFUL'::character varying])::text[])))
);


--
-- Name: TABLE app_chat_message_feedback; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.app_chat_message_feedback IS '业务用户对本人会话中助手回答的质量反馈';


--
-- Name: app_chat_message_feedback_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.app_chat_message_feedback_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: app_chat_message_feedback_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.app_chat_message_feedback_id_seq OWNED BY public.app_chat_message_feedback.id;


--
-- Name: app_chat_message_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.app_chat_message_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: app_chat_message_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.app_chat_message_id_seq OWNED BY public.app_chat_message.id;


--
-- Name: app_chat_retrieval_evidence; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.app_chat_retrieval_evidence (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    assistant_message_id bigint NOT NULL,
    trace_id character varying(128) NOT NULL,
    knowledge_base_id bigint NOT NULL,
    document_id bigint NOT NULL,
    chunk_id bigint NOT NULL,
    score numeric(10,6) NOT NULL,
    retrieval_signal character varying(64) NOT NULL,
    index_version character varying(128) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE app_chat_retrieval_evidence; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.app_chat_retrieval_evidence IS 'App Studio 调试运行召回证据快照表，支撑历史回放与审计查询';


--
-- Name: COLUMN app_chat_retrieval_evidence.assistant_message_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.app_chat_retrieval_evidence.assistant_message_id IS 'assistant 消息主键';


--
-- Name: COLUMN app_chat_retrieval_evidence.trace_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.app_chat_retrieval_evidence.trace_id IS '调试运行 Trace ID';


--
-- Name: COLUMN app_chat_retrieval_evidence.retrieval_signal; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.app_chat_retrieval_evidence.retrieval_signal IS '召回信号类型，如 VECTOR';


--
-- Name: COLUMN app_chat_retrieval_evidence.index_version; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.app_chat_retrieval_evidence.index_version IS '召回命中的索引版本';


--
-- Name: app_chat_retrieval_evidence_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.app_chat_retrieval_evidence_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: app_chat_retrieval_evidence_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.app_chat_retrieval_evidence_id_seq OWNED BY public.app_chat_retrieval_evidence.id;


--
-- Name: app_chat_run; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.app_chat_run (
    run_id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    session_id bigint NOT NULL,
    application_id bigint NOT NULL,
    actor_id bigint NOT NULL,
    client_message_id character varying(128) NOT NULL,
    command_hash character(64) NOT NULL,
    status character varying(24) NOT NULL,
    phase character varying(32) NOT NULL,
    lease_owner character varying(128) NOT NULL,
    lease_expires_at timestamp with time zone NOT NULL,
    fencing_token bigint DEFAULT 1 NOT NULL,
    attempt_count integer DEFAULT 1 NOT NULL,
    finalization_json jsonb,
    result_json jsonb,
    failure_code character varying(128),
    failure_summary character varying(512),
    completed_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    finalization_attempt_count integer DEFAULT 0 NOT NULL,
    finalization_cycle_attempt_count integer DEFAULT 0 NOT NULL,
    manual_replay_count integer DEFAULT 0 NOT NULL,
    failure_trace_id character varying(64),
    last_replay_request_id character varying(128),
    last_replay_reason character varying(512),
    CONSTRAINT ck_app_chat_run_command_hash CHECK ((command_hash ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT ck_app_chat_run_completed_payload CHECK (((((status)::text = 'COMPLETED'::text) AND (result_json IS NOT NULL) AND (completed_at IS NOT NULL)) OR (((status)::text <> 'COMPLETED'::text) AND (result_json IS NULL) AND (completed_at IS NULL)))),
    CONSTRAINT ck_app_chat_run_dead_letter_phase CHECK ((((status)::text <> 'DEAD_LETTER'::text) OR ((phase)::text = 'RESULT_RECEIVED'::text))),
    CONSTRAINT ck_app_chat_run_failed_phase CHECK ((((status)::text <> 'FAILED'::text) OR ((phase)::text = 'PREPARING'::text))),
    CONSTRAINT ck_app_chat_run_failure_fields CHECK (((((status)::text = ANY ((ARRAY['FAILED'::character varying, 'UNCERTAIN'::character varying, 'QUARANTINED'::character varying])::text[])) AND (failure_code IS NOT NULL) AND (failure_summary IS NOT NULL)) OR (((status)::text = 'DEAD_LETTER'::text) AND (failure_code IS NOT NULL) AND (failure_summary IS NOT NULL) AND (failure_trace_id IS NOT NULL)) OR (((status)::text = 'IN_PROGRESS'::text) AND ((phase)::text = 'RESULT_RECEIVED'::text) AND (((failure_code IS NULL) AND (failure_summary IS NULL) AND (failure_trace_id IS NULL)) OR ((failure_code IS NOT NULL) AND (failure_summary IS NOT NULL) AND (failure_trace_id IS NOT NULL)))) OR ((((status)::text = 'COMPLETED'::text) OR (((status)::text = 'IN_PROGRESS'::text) AND ((phase)::text <> 'RESULT_RECEIVED'::text))) AND (failure_code IS NULL) AND (failure_summary IS NULL) AND (failure_trace_id IS NULL)))),
    CONSTRAINT ck_app_chat_run_fencing CHECK (((fencing_token >= 1) AND (attempt_count >= 1))),
    CONSTRAINT ck_app_chat_run_finalization_payload CHECK (((((status)::text = ANY ((ARRAY['IN_PROGRESS'::character varying, 'DEAD_LETTER'::character varying])::text[])) AND ((phase)::text = 'RESULT_RECEIVED'::text) AND (finalization_json IS NOT NULL)) OR ((NOT (((status)::text = ANY ((ARRAY['IN_PROGRESS'::character varying, 'DEAD_LETTER'::character varying])::text[])) AND ((phase)::text = 'RESULT_RECEIVED'::text))) AND (finalization_json IS NULL)))),
    CONSTRAINT ck_app_chat_run_finalization_retry_counts CHECK (((finalization_attempt_count >= 0) AND (finalization_cycle_attempt_count >= 0) AND (manual_replay_count >= 0) AND (finalization_attempt_count >= finalization_cycle_attempt_count) AND (finalization_cycle_attempt_count <= 3) AND (((phase)::text <> 'RESULT_RECEIVED'::text) OR ((status)::text = ANY ((ARRAY['FAILED'::character varying, 'UNCERTAIN'::character varying, 'QUARANTINED'::character varying])::text[])) OR ((finalization_attempt_count >= 1) AND (finalization_cycle_attempt_count >= 1))))),
    CONSTRAINT ck_app_chat_run_finalization_schema CHECK ((((finalization_json IS NULL) OR ((jsonb_typeof(finalization_json) = 'object'::text) AND (finalization_json ? 'schemaVersion'::text) AND (jsonb_typeof((finalization_json -> 'schemaVersion'::text)) = 'number'::text) AND ((finalization_json -> 'schemaVersion'::text) = '1'::jsonb) AND (octet_length((finalization_json)::text) <= 1048576))) IS TRUE)),
    CONSTRAINT ck_app_chat_run_phase CHECK (((phase)::text = ANY ((ARRAY['PREPARING'::character varying, 'MODEL_DISPATCHED'::character varying, 'RESULT_RECEIVED'::character varying])::text[]))),
    CONSTRAINT ck_app_chat_run_quarantine_phase CHECK ((((status)::text <> 'QUARANTINED'::text) OR ((phase)::text = 'RESULT_RECEIVED'::text))),
    CONSTRAINT ck_app_chat_run_replay_request CHECK ((((manual_replay_count = 0) AND (last_replay_request_id IS NULL) AND (last_replay_reason IS NULL)) OR ((manual_replay_count >= 1) AND (last_replay_request_id IS NOT NULL) AND (last_replay_reason IS NOT NULL)))),
    CONSTRAINT ck_app_chat_run_result_schema CHECK ((((result_json IS NULL) OR ((jsonb_typeof(result_json) = 'object'::text) AND (result_json ? 'schemaVersion'::text) AND (jsonb_typeof((result_json -> 'schemaVersion'::text)) = 'number'::text) AND ((result_json -> 'schemaVersion'::text) = '1'::jsonb) AND (result_json ? 'result'::text) AND (jsonb_typeof((result_json -> 'result'::text)) = 'object'::text) AND (octet_length((result_json)::text) <= 1048576))) IS TRUE)),
    CONSTRAINT ck_app_chat_run_status CHECK (((status)::text = ANY ((ARRAY['IN_PROGRESS'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying, 'UNCERTAIN'::character varying, 'QUARANTINED'::character varying, 'DEAD_LETTER'::character varying])::text[]))),
    CONSTRAINT ck_app_chat_run_uncertain_phase CHECK ((((status)::text <> 'UNCERTAIN'::text) OR ((phase)::text = 'MODEL_DISPATCHED'::text)))
);


--
-- Name: TABLE app_chat_run; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.app_chat_run IS 'App Studio durable chat command/run state; MODEL_DISPATCHED is never auto-replayed, RESULT_RECEIVED replays only final Tx B';


--
-- Name: COLUMN app_chat_run.fencing_token; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.app_chat_run.fencing_token IS 'Monotonic ownership token; every state mutation must match tenant, run, lease_owner and fencing_token';


--
-- Name: COLUMN app_chat_run.finalization_json; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.app_chat_run.finalization_json IS 'Bounded v1 Tx B facts only; excludes composed model prompt/runtime credentials and is cleared on completion';


--
-- Name: COLUMN app_chat_run.result_json; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.app_chat_run.result_json IS 'Bounded explicit v1 completed-result envelope used for stable client-message replay';


--
-- Name: COLUMN app_chat_run.finalization_attempt_count; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.app_chat_run.finalization_attempt_count IS 'Monotonic total Tx B attempts, including initial completion and every automatic/manual replay';


--
-- Name: COLUMN app_chat_run.finalization_cycle_attempt_count; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.app_chat_run.finalization_cycle_attempt_count IS 'Attempts in the current bounded automatic recovery cycle; manual replay starts a new cycle at one';


--
-- Name: COLUMN app_chat_run.failure_trace_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.app_chat_run.failure_trace_id IS 'Safe trace identifier for the most recent finalization failure; never stores exception text or payload';


--
-- Name: COLUMN app_chat_run.last_replay_request_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.app_chat_run.last_replay_request_id IS 'Tenant-scoped idempotency key for the latest administrator-triggered dead-letter replay';


--
-- Name: app_chat_run_replay_request; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.app_chat_run_replay_request (
    request_id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    run_id bigint NOT NULL,
    replay_request_id character varying(128) NOT NULL,
    actor_id bigint NOT NULL,
    reason character varying(512) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_app_chat_run_replay_reason CHECK ((length(btrim((reason)::text)) > 0))
);


--
-- Name: TABLE app_chat_run_replay_request; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.app_chat_run_replay_request IS 'Append-only tenant idempotency ledger for administrator-triggered chat finalization dead-letter replay';


--
-- Name: app_chat_run_replay_request_request_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.app_chat_run_replay_request_request_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: app_chat_run_replay_request_request_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.app_chat_run_replay_request_request_id_seq OWNED BY public.app_chat_run_replay_request.request_id;


--
-- Name: app_chat_run_run_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.app_chat_run_run_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: app_chat_run_run_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.app_chat_run_run_id_seq OWNED BY public.app_chat_run.run_id;


--
-- Name: app_chat_session; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.app_chat_session (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    application_id bigint NOT NULL,
    user_id bigint NOT NULL,
    title character varying(255),
    status character varying(32) NOT NULL,
    last_message_at timestamp with time zone,
    trace_id character varying(64),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by bigint,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_by bigint,
    deleted_at timestamp with time zone,
    version integer DEFAULT 0 NOT NULL,
    release_id bigint,
    release_version bigint,
    CONSTRAINT ck_app_chat_session_release_pin CHECK (((((release_id IS NULL) AND (release_version IS NULL)) OR ((release_id IS NOT NULL) AND (release_version IS NOT NULL))) AND (((status)::text <> 'ACTIVE'::text) OR (release_id IS NOT NULL))))
);


--
-- Name: COLUMN app_chat_session.release_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.app_chat_session.release_id IS '会话创建时冻结的不可变应用发布版本 ID';


--
-- Name: COLUMN app_chat_session.release_version; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.app_chat_session.release_version IS '会话创建时冻结的不可变应用发布版本号';


--
-- Name: app_chat_session_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.app_chat_session_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: app_chat_session_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.app_chat_session_id_seq OWNED BY public.app_chat_session.id;


--
-- Name: app_model_usage_policy_event_receipt; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.app_model_usage_policy_event_receipt (
    event_id character varying(128) NOT NULL,
    event_version bigint NOT NULL,
    occurred_at timestamp with time zone NOT NULL,
    consumed_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_app_model_usage_policy_event_receipt_version CHECK ((event_version = ANY (ARRAY[(1)::bigint, (2)::bigint])))
);


--
-- Name: app_model_usage_policy_projection; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.app_model_usage_policy_projection (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    model_config_id bigint NOT NULL,
    provider_code character varying(64) NOT NULL,
    provider_kind character varying(64) NOT NULL,
    provider_endpoint character varying(512) NOT NULL,
    auth_type character varying(32) NOT NULL,
    secret_ref_id character varying(64),
    model_code character varying(128) NOT NULL,
    capabilities character varying(256) NOT NULL,
    allowed_use_cases character varying(256) NOT NULL,
    allowed_application_ids text DEFAULT ''::text NOT NULL,
    effective_options_json text NOT NULL,
    max_input_tokens integer NOT NULL,
    max_output_tokens integer NOT NULL,
    policy_version character varying(128) NOT NULL,
    expires_at timestamp with time zone,
    enabled boolean NOT NULL,
    source_event_id character varying(64) NOT NULL,
    source_event_version bigint NOT NULL,
    last_event_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    version integer DEFAULT 0 NOT NULL,
    provider_id bigint NOT NULL,
    model_config_version integer NOT NULL,
    model_type character varying(32) NOT NULL,
    effective_options_digest character varying(64) NOT NULL,
    policy_revision bigint NOT NULL,
    policy_digest character varying(64) NOT NULL,
    CONSTRAINT ck_app_model_usage_policy_config_version CHECK ((model_config_version >= 0)),
    CONSTRAINT ck_app_model_usage_policy_digest CHECK (((policy_digest)::text ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT ck_app_model_usage_policy_model_type CHECK (((btrim((model_type)::text) <> ''::text) AND ((model_type)::text = upper((model_type)::text)))),
    CONSTRAINT ck_app_model_usage_policy_options_digest CHECK (((effective_options_digest)::text ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT ck_app_model_usage_policy_revision CHECK (((policy_revision > 0) AND (policy_revision < '9223372036854775807'::bigint)))
);


--
-- Name: COLUMN app_model_usage_policy_projection.provider_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.app_model_usage_policy_projection.provider_id IS '权威模型供应商 ID；运行时凭证解析必须按此绑定';


--
-- Name: COLUMN app_model_usage_policy_projection.model_config_version; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.app_model_usage_policy_projection.model_config_version IS '权威模型配置乐观锁版本；与当前 mdl_config.version 不一致时禁止运行时调用';


--
-- Name: COLUMN app_model_usage_policy_projection.model_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.app_model_usage_policy_projection.model_type IS '权威模型类型；与当前 mdl_config.model_type 不一致时禁止运行时调用';


--
-- Name: COLUMN app_model_usage_policy_projection.effective_options_digest; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.app_model_usage_policy_projection.effective_options_digest IS 'PostgreSQL JSONB canonical effective options SHA-256；不一致时禁止解密和出网';


--
-- Name: COLUMN app_model_usage_policy_projection.policy_revision; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.app_model_usage_policy_projection.policy_revision IS 'model-hub 持久化单调策略修订号；不使用毫秒时间戳排序';


--
-- Name: COLUMN app_model_usage_policy_projection.policy_digest; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.app_model_usage_policy_projection.policy_digest IS '同一策略修订号的 SHA-256 载荷摘要；摘要冲突必须 fail-closed';


--
-- Name: app_model_usage_policy_projection_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.app_model_usage_policy_projection_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: app_model_usage_policy_projection_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.app_model_usage_policy_projection_id_seq OWNED BY public.app_model_usage_policy_projection.id;


--
-- Name: app_publish_preflight_result; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.app_publish_preflight_result (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    application_id bigint NOT NULL,
    publish_request_id character varying(128) NOT NULL,
    passed boolean DEFAULT false NOT NULL,
    blockers_json jsonb DEFAULT '[]'::jsonb NOT NULL,
    warnings_json jsonb DEFAULT '[]'::jsonb NOT NULL,
    trace_id character varying(128),
    checked_by bigint NOT NULL,
    checked_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    candidate_fingerprint character varying(64) NOT NULL,
    CONSTRAINT ck_app_publish_preflight_candidate_fingerprint CHECK (((candidate_fingerprint)::text ~ '^[0-9a-f]{64}$'::text))
);


--
-- Name: TABLE app_publish_preflight_result; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.app_publish_preflight_result IS '应用发布预检结果和阻断原因';


--
-- Name: COLUMN app_publish_preflight_result.candidate_fingerprint; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.app_publish_preflight_result.candidate_fingerprint IS 'SHA-256 of the immutable release candidate and its sorted effective audience';


--
-- Name: app_publish_preflight_result_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.app_publish_preflight_result_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: app_publish_preflight_result_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.app_publish_preflight_result_id_seq OWNED BY public.app_publish_preflight_result.id;


--
-- Name: appr_decision_outbox; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.appr_decision_outbox (
    id bigint NOT NULL,
    event_id character varying(255) NOT NULL,
    event_type character varying(128) NOT NULL,
    tenant_id bigint NOT NULL,
    aggregate_type character varying(128) NOT NULL,
    aggregate_id character varying(128) NOT NULL,
    event_version bigint NOT NULL,
    schema_version character varying(32) NOT NULL,
    occurred_at timestamp with time zone NOT NULL,
    trace_id character varying(128) NOT NULL,
    producer character varying(128) NOT NULL,
    payload_json jsonb NOT NULL,
    status character varying(32) DEFAULT 'PENDING'::character varying NOT NULL,
    attempts integer DEFAULT 0 NOT NULL,
    next_attempt_at timestamp with time zone DEFAULT now() NOT NULL,
    lease_owner character varying(128),
    lease_until timestamp with time zone,
    published_at timestamp with time zone,
    last_error character varying(128),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_appr_decision_outbox_attempts CHECK ((attempts >= 0)),
    CONSTRAINT ck_appr_decision_outbox_lease CHECK (((((status)::text = 'IN_FLIGHT'::text) AND (lease_owner IS NOT NULL) AND (lease_until IS NOT NULL)) OR (((status)::text <> 'IN_FLIGHT'::text) AND (lease_owner IS NULL) AND (lease_until IS NULL)))),
    CONSTRAINT ck_appr_decision_outbox_protocol CHECK (COALESCE((((event_type)::text = 'ApprovalDecisionRecorded'::text) AND ((aggregate_type)::text = 'ApprovalTask'::text) AND (event_version = 1) AND ((producer)::text = 'approval-center'::text) AND ((schema_version)::text = ANY ((ARRAY['v1'::character varying, 'v2'::character varying, 'v3'::character varying])::text[])) AND ((((schema_version)::text = ANY ((ARRAY['v1'::character varying, 'v2'::character varying])::text[])) AND ((status)::text = 'PUBLISHED'::text)) OR (((schema_version)::text = 'v3'::text) AND ((payload_json ->> 'approvalRequestId'::text) IS NOT NULL) AND (btrim((payload_json ->> 'approvalRequestId'::text)) <> ''::text) AND ((payload_json ->> 'evidenceSchema'::text) = 'model-approval-evidence/v1'::text) AND ((payload_json ->> 'evidenceDigest'::text) ~ '^[0-9a-f]{64}$'::text) AND ((payload_json ->> 'targetDigest'::text) ~ '^[0-9a-f]{64}$'::text)))), false)),
    CONSTRAINT ck_appr_decision_outbox_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'IN_FLIGHT'::character varying, 'PUBLISHED'::character varying])::text[])))
);


--
-- Name: appr_decision_outbox_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.appr_decision_outbox_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: appr_decision_outbox_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.appr_decision_outbox_id_seq OWNED BY public.appr_decision_outbox.id;


--
-- Name: appr_model_approval_cancellation; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.appr_model_approval_cancellation (
    id bigint NOT NULL,
    event_id character varying(128) NOT NULL,
    tenant_id bigint NOT NULL,
    approval_request_id character varying(128) NOT NULL,
    source_context character varying(64) NOT NULL,
    source_resource_type character varying(64) NOT NULL,
    source_resource_id character varying(64) NOT NULL,
    actor_id bigint NOT NULL,
    reason character varying(512) NOT NULL,
    trace_id character varying(128) NOT NULL,
    requested_at timestamp with time zone NOT NULL,
    status character varying(32) DEFAULT 'PENDING'::character varying NOT NULL,
    task_id bigint,
    resolved_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_appr_model_approval_cancellation_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPLIED'::character varying, 'DECISION_WON'::character varying])::text[]))),
    CONSTRAINT ck_appr_model_approval_cancellation_terminal CHECK (((((status)::text = 'PENDING'::text) AND (task_id IS NULL) AND (resolved_at IS NULL)) OR (((status)::text <> 'PENDING'::text) AND (task_id IS NOT NULL) AND (resolved_at IS NOT NULL)))),
    CONSTRAINT ck_appr_model_approval_cancellation_text CHECK (((btrim((approval_request_id)::text) <> ''::text) AND ((source_context)::text = 'model-hub'::text) AND ((source_resource_type)::text = ANY ((ARRAY['model_provider'::character varying, 'model_config'::character varying])::text[])) AND (btrim((source_resource_id)::text) <> ''::text) AND (btrim((reason)::text) <> ''::text) AND (char_length((reason)::text) <= 500) AND ((reason)::text !~ '[[:cntrl:]]'::text) AND (btrim((trace_id)::text) <> ''::text)))
);


--
-- Name: TABLE appr_model_approval_cancellation; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.appr_model_approval_cancellation IS 'model-hub 审批撤销 inbox；PENDING 可跨 MQ 乱序等待任务，APPLIED/DECISION_WON 固化 CAS 单胜者';


--
-- Name: appr_model_approval_cancellation_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.appr_model_approval_cancellation_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: appr_model_approval_cancellation_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.appr_model_approval_cancellation_id_seq OWNED BY public.appr_model_approval_cancellation.id;


--
-- Name: appr_record; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.appr_record (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    task_id bigint NOT NULL,
    actor_id bigint NOT NULL,
    action character varying(32) NOT NULL,
    comment text,
    action_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: appr_record_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.appr_record_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: appr_record_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.appr_record_id_seq OWNED BY public.appr_record.id;


--
-- Name: appr_task; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.appr_task (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    approval_type character varying(64) NOT NULL,
    source_context character varying(64) NOT NULL,
    source_resource_type character varying(64) NOT NULL,
    source_resource_id bigint NOT NULL,
    title character varying(255) NOT NULL,
    risk_level character varying(32) NOT NULL,
    requester_id bigint NOT NULL,
    approver_id bigint,
    status character varying(32) NOT NULL,
    due_at timestamp with time zone,
    decision_at timestamp with time zone,
    trace_id character varying(64),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by bigint,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_by bigint,
    deleted_at timestamp with time zone,
    version integer DEFAULT 0 NOT NULL,
    approval_request_id character varying(128) NOT NULL,
    evidence_schema character varying(64),
    evidence_digest character varying(64),
    target_digest character varying(64),
    evidence_json jsonb,
    CONSTRAINT ck_appr_task_approval_request_id_not_blank CHECK ((btrim((approval_request_id)::text) <> ''::text)),
    CONSTRAINT ck_appr_task_evidence_all_or_none CHECK (COALESCE((((evidence_schema IS NULL) AND (evidence_digest IS NULL) AND (target_digest IS NULL) AND (evidence_json IS NULL)) OR (((evidence_schema)::text = 'model-approval-evidence/v1'::text) AND ((evidence_digest)::text ~ '^[0-9a-f]{64}$'::text) AND ((target_digest)::text ~ '^[0-9a-f]{64}$'::text) AND (jsonb_typeof(evidence_json) = 'object'::text) AND ((evidence_json ->> 'tenantId'::text) = (tenant_id)::text) AND ((evidence_json ->> 'approvalRequestId'::text) = (approval_request_id)::text) AND ((evidence_json ->> 'approvalType'::text) = (approval_type)::text) AND ((evidence_json ->> 'sourceContext'::text) = (source_context)::text) AND ((evidence_json ->> 'resourceType'::text) = (source_resource_type)::text) AND ((evidence_json ->> 'resourceId'::text) = (source_resource_id)::text) AND ((evidence_json ->> 'requesterId'::text) = (requester_id)::text) AND ((evidence_json ->> 'riskLevel'::text) = (risk_level)::text))), false)),
    CONSTRAINT ck_appr_task_pending_model_evidence CHECK (COALESCE(((NOT (((status)::text = 'PENDING'::text) AND (deleted_at IS NULL) AND ((source_context)::text = 'model-hub'::text) AND ((approval_type)::text = ANY ((ARRAY['model_provider_change'::character varying, 'model_config_change'::character varying])::text[])) AND ((source_resource_type)::text = ANY ((ARRAY['model_provider'::character varying, 'model_config'::character varying])::text[])))) OR (evidence_schema IS NOT NULL)), false)),
    CONSTRAINT ck_appr_task_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying, 'RETURNED'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: appr_task_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.appr_task_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: appr_task_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.appr_task_id_seq OWNED BY public.appr_task.id;


--
-- Name: audit_operation_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.audit_operation_log (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    actor_id bigint,
    action_code character varying(128) NOT NULL,
    resource_type character varying(64),
    resource_id bigint,
    request_method character varying(16),
    request_path character varying(512),
    client_ip character varying(64),
    user_agent character varying(512),
    result_status character varying(32) NOT NULL,
    detail_json jsonb,
    trace_id character varying(64),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    resource_ref character varying(128)
);


--
-- Name: COLUMN audit_operation_log.resource_ref; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.audit_operation_log.resource_ref IS 'Opaque domain resource reference; resource_id remains an optional BIGINT compatibility projection';


--
-- Name: audit_operation_log_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.audit_operation_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: audit_operation_log_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.audit_operation_log_id_seq OWNED BY public.audit_operation_log.id;


--
-- Name: audit_security_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.audit_security_log (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    actor_id bigint,
    event_type character varying(64) NOT NULL,
    risk_level character varying(32) NOT NULL,
    subject_type character varying(64),
    subject_id bigint,
    result_status character varying(32) NOT NULL,
    detail_json jsonb,
    client_ip character varying(64),
    trace_id character varying(64),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    subject_ref character varying(128)
);


--
-- Name: COLUMN audit_security_log.subject_ref; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.audit_security_log.subject_ref IS 'Opaque security subject reference; subject_id remains an optional BIGINT compatibility projection';


--
-- Name: audit_security_log_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.audit_security_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: audit_security_log_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.audit_security_log_id_seq OWNED BY public.audit_security_log.id;


--
-- Name: iam_delegated_tenant_action; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.iam_delegated_tenant_action (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    operator_id bigint NOT NULL,
    action_type character varying(64) NOT NULL,
    target_resource_type character varying(64) NOT NULL,
    target_resource_id character varying(128) NOT NULL,
    reason character varying(512) NOT NULL,
    request_hash character varying(128) NOT NULL,
    audit_event_id character varying(128) NOT NULL,
    status character varying(32) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_iam_delegated_tenant_action_status CHECK (((status)::text = ANY ((ARRAY['SUCCEEDED'::character varying, 'FAILED'::character varying])::text[])))
);


--
-- Name: TABLE iam_delegated_tenant_action; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.iam_delegated_tenant_action IS '平台管理员代管租户操作记录';


--
-- Name: COLUMN iam_delegated_tenant_action.reason; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.iam_delegated_tenant_action.reason IS '代管原因，必填并进入审计';


--
-- Name: iam_delegated_tenant_action_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.iam_delegated_tenant_action_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: iam_delegated_tenant_action_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.iam_delegated_tenant_action_id_seq OWNED BY public.iam_delegated_tenant_action.id;


--
-- Name: iam_department; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.iam_department (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    parent_id bigint,
    dept_code character varying(64) NOT NULL,
    dept_name character varying(128) NOT NULL,
    path character varying(512) NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    status character varying(32) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by bigint,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_by bigint,
    deleted_at timestamp with time zone,
    version integer DEFAULT 0 NOT NULL
);


--
-- Name: iam_department_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.iam_department_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: iam_department_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.iam_department_id_seq OWNED BY public.iam_department.id;


--
-- Name: iam_menu; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.iam_menu (
    id bigint NOT NULL,
    menu_code character varying(128) NOT NULL,
    parent_id bigint,
    menu_name character varying(128) NOT NULL,
    scope character varying(32) NOT NULL,
    path character varying(256) NOT NULL,
    route_name character varying(128) NOT NULL,
    component_key character varying(128) NOT NULL,
    icon character varying(64),
    required_permission_code character varying(128) NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    status character varying(32) NOT NULL,
    system_builtin boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by bigint,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_by bigint,
    deleted_at timestamp with time zone,
    version integer DEFAULT 0 NOT NULL
);


--
-- Name: iam_menu_feature; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.iam_menu_feature (
    id bigint NOT NULL,
    menu_id bigint NOT NULL,
    feature_code character varying(128) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: iam_menu_feature_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.iam_menu_feature_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: iam_menu_feature_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.iam_menu_feature_id_seq OWNED BY public.iam_menu_feature.id;


--
-- Name: iam_menu_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.iam_menu_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: iam_menu_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.iam_menu_id_seq OWNED BY public.iam_menu.id;


--
-- Name: iam_notification_outbox; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.iam_notification_outbox (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    aggregate_type character varying(64) NOT NULL,
    aggregate_id character varying(128) NOT NULL,
    template_code character varying(64) NOT NULL,
    recipient character varying(256) NOT NULL,
    payload_json text DEFAULT '{}'::text NOT NULL,
    delivery_status character varying(32) NOT NULL,
    attempts integer DEFAULT 0 NOT NULL,
    last_error character varying(512),
    delivered_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: iam_notification_outbox_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.iam_notification_outbox_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: iam_notification_outbox_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.iam_notification_outbox_id_seq OWNED BY public.iam_notification_outbox.id;


--
-- Name: iam_permission; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.iam_permission (
    id bigint NOT NULL,
    permission_code character varying(128) NOT NULL,
    permission_name character varying(128) NOT NULL,
    resource_type character varying(64) NOT NULL,
    action_code character varying(64) NOT NULL,
    description text,
    status character varying(32) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: iam_permission_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.iam_permission_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: iam_permission_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.iam_permission_id_seq OWNED BY public.iam_permission.id;


--
-- Name: iam_permission_request; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.iam_permission_request (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    request_id character varying(64) NOT NULL,
    requester_id bigint NOT NULL,
    resource_type character varying(64) NOT NULL,
    resource_id character varying(128) NOT NULL,
    permission_code character varying(128) NOT NULL,
    reason character varying(512) NOT NULL,
    status character varying(32) NOT NULL,
    reviewer_id bigint,
    decision_comment character varying(512),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT iam_permission_request_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[])))
);


--
-- Name: TABLE iam_permission_request; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.iam_permission_request IS '权限受理与人工决策工单，不直接扩大业务资源 ACL';


--
-- Name: iam_permission_request_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.iam_permission_request_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: iam_permission_request_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.iam_permission_request_id_seq OWNED BY public.iam_permission_request.id;


--
-- Name: iam_resource_acl; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.iam_resource_acl (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    resource_type character varying(64) NOT NULL,
    resource_id bigint NOT NULL,
    subject_type character varying(32) NOT NULL,
    subject_id bigint NOT NULL,
    permission_code character varying(128) NOT NULL,
    effect character varying(16) NOT NULL,
    expires_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by bigint,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_by bigint,
    deleted_at timestamp with time zone,
    version integer DEFAULT 0 NOT NULL
);


--
-- Name: iam_resource_acl_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.iam_resource_acl_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: iam_resource_acl_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.iam_resource_acl_id_seq OWNED BY public.iam_resource_acl.id;


--
-- Name: iam_role; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.iam_role (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    role_code character varying(64) NOT NULL,
    role_name character varying(128) NOT NULL,
    role_type character varying(32) NOT NULL,
    default_menu_code character varying(128),
    status character varying(32) NOT NULL,
    description text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by bigint,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_by bigint,
    deleted_at timestamp with time zone,
    version integer DEFAULT 0 NOT NULL
);


--
-- Name: iam_role_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.iam_role_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: iam_role_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.iam_role_id_seq OWNED BY public.iam_role.id;


--
-- Name: iam_role_menu; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.iam_role_menu (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    role_id bigint NOT NULL,
    menu_id bigint NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by bigint,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_by bigint,
    deleted_at timestamp with time zone,
    version integer DEFAULT 0 NOT NULL
);


--
-- Name: iam_role_menu_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.iam_role_menu_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: iam_role_menu_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.iam_role_menu_id_seq OWNED BY public.iam_role_menu.id;


--
-- Name: iam_role_permission; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.iam_role_permission (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    role_id bigint NOT NULL,
    permission_id bigint NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by bigint,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_by bigint,
    deleted_at timestamp with time zone,
    version integer DEFAULT 0 NOT NULL
);


--
-- Name: iam_role_permission_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.iam_role_permission_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: iam_role_permission_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.iam_role_permission_id_seq OWNED BY public.iam_role_permission.id;


--
-- Name: iam_tenant; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.iam_tenant (
    id bigint NOT NULL,
    tenant_code character varying(64) NOT NULL,
    tenant_name character varying(128) NOT NULL,
    status character varying(32) NOT NULL,
    plan_code character varying(64),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version integer DEFAULT 0 NOT NULL
);


--
-- Name: iam_tenant_feature; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.iam_tenant_feature (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    feature_code character varying(128) NOT NULL,
    feature_name character varying(128) NOT NULL,
    enabled boolean NOT NULL,
    effective_from timestamp with time zone,
    effective_to timestamp with time zone,
    source character varying(32) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by bigint,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_by bigint,
    version integer DEFAULT 0 NOT NULL
);


--
-- Name: iam_tenant_feature_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.iam_tenant_feature_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: iam_tenant_feature_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.iam_tenant_feature_id_seq OWNED BY public.iam_tenant_feature.id;


--
-- Name: iam_tenant_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.iam_tenant_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: iam_tenant_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.iam_tenant_id_seq OWNED BY public.iam_tenant.id;


--
-- Name: iam_user; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.iam_user (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    department_id bigint,
    username character varying(64) NOT NULL,
    display_name character varying(128) NOT NULL,
    email character varying(128),
    mobile character varying(32),
    password_hash character varying(255) NOT NULL,
    status character varying(32) NOT NULL,
    last_login_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by bigint,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_by bigint,
    deleted_at timestamp with time zone,
    version integer DEFAULT 0 NOT NULL
);


--
-- Name: iam_user_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.iam_user_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: iam_user_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.iam_user_id_seq OWNED BY public.iam_user.id;


--
-- Name: iam_user_invitation; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.iam_user_invitation (
    id character varying(64) NOT NULL,
    tenant_id bigint NOT NULL,
    user_id bigint NOT NULL,
    role_id bigint NOT NULL,
    email character varying(128) NOT NULL,
    token_digest character varying(128) NOT NULL,
    status character varying(32) NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    invited_by bigint NOT NULL,
    accepted_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: iam_user_role; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.iam_user_role (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    user_id bigint NOT NULL,
    role_id bigint NOT NULL,
    grant_source character varying(32) NOT NULL,
    expires_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by bigint,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_by bigint,
    deleted_at timestamp with time zone,
    version integer DEFAULT 0 NOT NULL
);


--
-- Name: iam_user_role_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.iam_user_role_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: iam_user_role_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.iam_user_role_id_seq OWNED BY public.iam_user_role.id;


--
-- Name: kb_citation_record; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.kb_citation_record (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    chat_message_id bigint NOT NULL,
    knowledge_base_id bigint NOT NULL,
    document_id bigint NOT NULL,
    chunk_id bigint NOT NULL,
    score numeric(8,6) NOT NULL,
    citation_text text,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: kb_citation_record_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.kb_citation_record_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: kb_citation_record_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.kb_citation_record_id_seq OWNED BY public.kb_citation_record.id;


--
-- Name: kb_document; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.kb_document (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    knowledge_base_id bigint NOT NULL,
    document_name character varying(255) NOT NULL,
    source_type character varying(32) NOT NULL,
    source_uri character varying(1024),
    content_hash character varying(128) NOT NULL,
    mime_type character varying(128),
    file_size_bytes bigint,
    parse_status character varying(32) NOT NULL,
    failure_reason text,
    version_no integer DEFAULT 1 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by bigint,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_by bigint,
    deleted_at timestamp with time zone,
    version integer DEFAULT 0 NOT NULL
);


--
-- Name: kb_document_chunk; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.kb_document_chunk (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    knowledge_base_id bigint NOT NULL,
    document_id bigint NOT NULL,
    chunk_no integer NOT NULL,
    content text NOT NULL,
    content_hash character varying(128) NOT NULL,
    source_uri text NOT NULL,
    document_version integer NOT NULL,
    title_path character varying(512) NOT NULL,
    paragraph_no integer,
    table_cell_range character varying(128),
    classification character varying(64) NOT NULL,
    visibility character varying(64) NOT NULL,
    acl_tokens text NOT NULL,
    authoritative boolean DEFAULT false NOT NULL,
    published_at timestamp with time zone NOT NULL,
    expires_at timestamp with time zone,
    embedding_model character varying(128) NOT NULL,
    chunk_strategy_version character varying(64) NOT NULL,
    index_version character varying(128) NOT NULL,
    embedding public.vector,
    token_count integer DEFAULT 0 NOT NULL,
    page_no integer,
    position_start integer,
    position_end integer,
    status character varying(32) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by bigint,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_by bigint,
    deleted_at timestamp with time zone,
    version integer DEFAULT 0 NOT NULL
);


--
-- Name: COLUMN kb_document_chunk.embedding; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_document_chunk.embedding IS '切片 embedding 向量；维度由知识库当前挂载的运行时向量模型决定，切换模型必须探活并重建索引';


--
-- Name: kb_document_chunk_embedding; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.kb_document_chunk_embedding (
    tenant_id bigint NOT NULL,
    knowledge_base_id bigint NOT NULL,
    document_id bigint NOT NULL,
    chunk_id bigint NOT NULL,
    embedding_model character varying(128) NOT NULL,
    index_version character varying(128) NOT NULL,
    embedding public.vector NOT NULL,
    indexed_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by bigint,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_by bigint,
    version integer DEFAULT 0 NOT NULL,
    CONSTRAINT kb_document_chunk_embedding_embedding_model_check CHECK ((btrim((embedding_model)::text) <> ''::text)),
    CONSTRAINT kb_document_chunk_embedding_index_version_check CHECK ((btrim((index_version)::text) <> ''::text))
);


--
-- Name: TABLE kb_document_chunk_embedding; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.kb_document_chunk_embedding IS '按索引版本并行保存知识切片向量；候选版本完整前不得替换 active 索引';


--
-- Name: kb_document_chunk_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.kb_document_chunk_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: kb_document_chunk_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.kb_document_chunk_id_seq OWNED BY public.kb_document_chunk.id;


--
-- Name: kb_document_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.kb_document_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: kb_document_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.kb_document_id_seq OWNED BY public.kb_document.id;


--
-- Name: kb_document_object_cleanup; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.kb_document_object_cleanup (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    knowledge_base_id bigint NOT NULL,
    document_id bigint NOT NULL,
    bucket character varying(255) NOT NULL,
    object_key character varying(2048) NOT NULL,
    source_uri character varying(4096) NOT NULL,
    observation_failure_type character varying(96) NOT NULL,
    status character varying(32) DEFAULT 'PENDING'::character varying NOT NULL,
    attempt_count integer DEFAULT 0 NOT NULL,
    next_attempt_at timestamp with time zone,
    lease_owner character varying(128),
    lease_token character varying(128),
    lease_until timestamp with time zone,
    failure_code character varying(64),
    failure_reason character varying(512),
    observed_at timestamp with time zone NOT NULL,
    completed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    version integer DEFAULT 0 NOT NULL,
    CONSTRAINT ck_kb_object_cleanup_failure CHECK (((((status)::text = ANY ((ARRAY['RETRY_WAIT'::character varying, 'FAILED'::character varying])::text[])) AND (btrim((COALESCE(failure_code, ''::character varying))::text) <> ''::text) AND (btrim((COALESCE(failure_reason, ''::character varying))::text) <> ''::text)) OR (((status)::text <> ALL ((ARRAY['RETRY_WAIT'::character varying, 'FAILED'::character varying])::text[])) AND (failure_code IS NULL) AND (failure_reason IS NULL)))),
    CONSTRAINT ck_kb_object_cleanup_lease CHECK (((((status)::text = 'PROCESSING'::text) AND (btrim((COALESCE(lease_owner, ''::character varying))::text) <> ''::text) AND (btrim((COALESCE(lease_token, ''::character varying))::text) <> ''::text) AND (lease_until IS NOT NULL)) OR (((status)::text <> 'PROCESSING'::text) AND (lease_owner IS NULL) AND (lease_token IS NULL) AND (lease_until IS NULL)))),
    CONSTRAINT ck_kb_object_cleanup_schedule CHECK (((((status)::text = ANY ((ARRAY['PENDING'::character varying, 'RETRY_WAIT'::character varying])::text[])) AND (next_attempt_at IS NOT NULL)) OR (((status)::text <> ALL ((ARRAY['PENDING'::character varying, 'RETRY_WAIT'::character varying])::text[])) AND (next_attempt_at IS NULL)))),
    CONSTRAINT ck_kb_object_cleanup_tenant_key CHECK (((object_key)::text ~~ (((((('tenants/'::text || tenant_id) || '/knowledge-bases/'::text) || knowledge_base_id) || '/documents/'::text) || document_id) || '/%'::text))),
    CONSTRAINT ck_kb_object_cleanup_terminal CHECK (((((status)::text = ANY ((ARRAY['COMPLETED'::character varying, 'RETAINED'::character varying, 'FAILED'::character varying])::text[])) AND (completed_at IS NOT NULL)) OR (((status)::text <> ALL ((ARRAY['COMPLETED'::character varying, 'RETAINED'::character varying, 'FAILED'::character varying])::text[])) AND (completed_at IS NULL)))),
    CONSTRAINT kb_document_object_cleanup_attempt_count_check CHECK (((attempt_count >= 0) AND (attempt_count <= 6))),
    CONSTRAINT kb_document_object_cleanup_bucket_check CHECK ((btrim((bucket)::text) <> ''::text)),
    CONSTRAINT kb_document_object_cleanup_object_key_check CHECK ((btrim((object_key)::text) <> ''::text)),
    CONSTRAINT kb_document_object_cleanup_observation_failure_type_check CHECK ((btrim((observation_failure_type)::text) <> ''::text)),
    CONSTRAINT kb_document_object_cleanup_source_uri_check CHECK ((btrim((source_uri)::text) <> ''::text)),
    CONSTRAINT kb_document_object_cleanup_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PROCESSING'::character varying, 'RETRY_WAIT'::character varying, 'COMPLETED'::character varying, 'RETAINED'::character varying, 'FAILED'::character varying])::text[])))
);


--
-- Name: TABLE kb_document_object_cleanup; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.kb_document_object_cleanup IS 'object-first 上传提交失败后的 durable、tenant-scoped、fenced 孤儿对象清理事实';


--
-- Name: kb_document_object_cleanup_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.kb_document_object_cleanup_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: kb_document_object_cleanup_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.kb_document_object_cleanup_id_seq OWNED BY public.kb_document_object_cleanup.id;


--
-- Name: kb_embedding_job; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.kb_embedding_job (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    knowledge_base_id bigint NOT NULL,
    document_id bigint NOT NULL,
    job_type character varying(32) NOT NULL,
    status character varying(32) NOT NULL,
    retry_count integer DEFAULT 0 NOT NULL,
    next_retry_at timestamp with time zone,
    failure_reason text,
    trace_id character varying(64) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by bigint,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_by bigint,
    deleted_at timestamp with time zone,
    version integer DEFAULT 0 NOT NULL,
    failure_code character varying(64),
    lease_owner character varying(128),
    lease_token character varying(128),
    lease_until timestamp with time zone,
    rebuild_id bigint,
    target_model_config_id bigint,
    target_embedding_model character varying(128),
    index_version character varying(128),
    operator_type character varying(64),
    cancel_reason character varying(300),
    cancelled_by bigint,
    cancel_trace_id character varying(64),
    cancelled_at timestamp with time zone,
    CONSTRAINT ck_kb_job_cancellation CHECK (((((status)::text = 'CANCELLED'::text) AND (btrim((COALESCE(cancel_reason, ''::character varying))::text) <> ''::text) AND (cancelled_by IS NOT NULL) AND (btrim((COALESCE(cancel_trace_id, ''::character varying))::text) <> ''::text) AND (cancelled_at IS NOT NULL)) OR (((status)::text <> 'CANCELLED'::text) AND (cancel_reason IS NULL) AND (cancelled_by IS NULL) AND (cancel_trace_id IS NULL) AND (cancelled_at IS NULL)))),
    CONSTRAINT ck_kb_job_failure CHECK (((((status)::text = ANY ((ARRAY['RETRY_WAIT'::character varying, 'FAILED'::character varying])::text[])) AND (btrim((COALESCE(failure_code, ''::character varying))::text) <> ''::text) AND (btrim(COALESCE(failure_reason, ''::text)) <> ''::text)) OR (((status)::text <> ALL ((ARRAY['RETRY_WAIT'::character varying, 'FAILED'::character varying])::text[])) AND (failure_code IS NULL) AND (failure_reason IS NULL)))),
    CONSTRAINT ck_kb_job_index_target CHECK (((((job_type)::text = 'DOCUMENT_PARSE'::text) AND (rebuild_id IS NULL) AND (target_model_config_id IS NULL) AND (target_embedding_model IS NULL) AND (index_version IS NULL) AND (operator_type IS NULL)) OR (((job_type)::text = 'DOCUMENT_INDEX'::text) AND (((rebuild_id IS NOT NULL) AND (target_model_config_id IS NOT NULL) AND (btrim((COALESCE(target_embedding_model, ''::character varying))::text) <> ''::text) AND (btrim((COALESCE(index_version, ''::character varying))::text) <> ''::text) AND (btrim((COALESCE(operator_type, ''::character varying))::text) <> ''::text)) OR (((status)::text = ANY ((ARRAY['COMPLETED'::character varying, 'FAILED'::character varying])::text[])) AND (rebuild_id IS NULL) AND (target_model_config_id IS NULL) AND (target_embedding_model IS NULL) AND (index_version IS NULL) AND (operator_type IS NULL)))))),
    CONSTRAINT ck_kb_job_lease CHECK (((((status)::text = ANY ((ARRAY['PARSING'::character varying, 'INDEXING'::character varying])::text[])) AND (btrim((COALESCE(lease_owner, ''::character varying))::text) <> ''::text) AND (btrim((COALESCE(lease_token, ''::character varying))::text) <> ''::text) AND (lease_until IS NOT NULL)) OR (((status)::text <> ALL ((ARRAY['PARSING'::character varying, 'INDEXING'::character varying])::text[])) AND (lease_owner IS NULL) AND (lease_token IS NULL) AND (lease_until IS NULL)))),
    CONSTRAINT ck_kb_job_retry_count CHECK (((retry_count >= 0) AND (retry_count <= 6))),
    CONSTRAINT ck_kb_job_schedule CHECK (((((status)::text = ANY ((ARRAY['PENDING'::character varying, 'RETRY_WAIT'::character varying])::text[])) AND (next_retry_at IS NOT NULL)) OR (((status)::text <> ALL ((ARRAY['PENDING'::character varying, 'RETRY_WAIT'::character varying])::text[])) AND (next_retry_at IS NULL)))),
    CONSTRAINT ck_kb_job_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'RETRY_WAIT'::character varying, 'PARSING'::character varying, 'INDEXING'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying, 'CANCELLED'::character varying])::text[]))),
    CONSTRAINT ck_kb_job_type CHECK (((job_type)::text = ANY ((ARRAY['DOCUMENT_PARSE'::character varying, 'DOCUMENT_INDEX'::character varying])::text[])))
);


--
-- Name: COLUMN kb_embedding_job.lease_token; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_embedding_job.lease_token IS '每次 claim 唯一 token，与 version 共同 fencing 迟到 worker';


--
-- Name: kb_embedding_job_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.kb_embedding_job_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: kb_embedding_job_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.kb_embedding_job_id_seq OWNED BY public.kb_embedding_job.id;


--
-- Name: kb_knowledge_base; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.kb_knowledge_base (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    kb_code character varying(64) NOT NULL,
    kb_name character varying(128) NOT NULL,
    description text,
    visibility character varying(32) NOT NULL,
    embedding_model_config_id bigint,
    status character varying(32) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by bigint,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_by bigint,
    deleted_at timestamp with time zone,
    version integer DEFAULT 0 NOT NULL
);


--
-- Name: kb_knowledge_base_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.kb_knowledge_base_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: kb_knowledge_base_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.kb_knowledge_base_id_seq OWNED BY public.kb_knowledge_base.id;


--
-- Name: kb_knowledge_index_active; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.kb_knowledge_index_active (
    tenant_id bigint NOT NULL,
    knowledge_base_id bigint NOT NULL,
    embedding_model character varying(128) NOT NULL,
    active_index_version character varying(128) NOT NULL,
    activated_at timestamp with time zone NOT NULL,
    activated_by bigint,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_by bigint,
    version integer DEFAULT 0 NOT NULL,
    CONSTRAINT kb_knowledge_index_active_active_index_version_check CHECK ((btrim((active_index_version)::text) <> ''::text)),
    CONSTRAINT kb_knowledge_index_active_embedding_model_check CHECK ((btrim((embedding_model)::text) <> ''::text))
);


--
-- Name: TABLE kb_knowledge_index_active; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.kb_knowledge_index_active IS '知识库检索唯一 active 索引真相源；切换由覆盖完整性 SQL 原子执行';


--
-- Name: kb_knowledge_index_desired; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.kb_knowledge_index_desired (
    tenant_id bigint NOT NULL,
    knowledge_base_id bigint NOT NULL,
    desired_rebuild_id bigint NOT NULL,
    desired_generation bigint NOT NULL,
    next_generation_required boolean DEFAULT false NOT NULL,
    requested_by bigint NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    version integer DEFAULT 0 NOT NULL,
    CONSTRAINT kb_knowledge_index_desired_desired_generation_check CHECK ((desired_generation > 0))
);


--
-- Name: TABLE kb_knowledge_index_desired; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.kb_knowledge_index_desired IS '每个知识库唯一 desired/latest generation 守卫与下一代脏标记';


--
-- Name: kb_knowledge_index_rebuild; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.kb_knowledge_index_rebuild (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    knowledge_base_id bigint NOT NULL,
    generation bigint NOT NULL,
    target_model_config_id bigint NOT NULL,
    target_embedding_model character varying(128) NOT NULL,
    index_version character varying(128) NOT NULL,
    status character varying(32) NOT NULL,
    failure_code character varying(64),
    failure_reason character varying(512),
    members_frozen_at timestamp with time zone NOT NULL,
    completed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by bigint NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_by bigint NOT NULL,
    version integer DEFAULT 0 NOT NULL,
    CONSTRAINT ck_kb_index_rebuild_terminal CHECK (((((status)::text = 'ACTIVE'::text) AND (completed_at IS NULL) AND (failure_code IS NULL) AND (failure_reason IS NULL)) OR (((status)::text = 'COMPLETED'::text) AND (completed_at IS NOT NULL) AND (failure_code IS NULL) AND (failure_reason IS NULL)) OR (((status)::text = 'FAILED'::text) AND (completed_at IS NOT NULL) AND (btrim((COALESCE(failure_code, ''::character varying))::text) <> ''::text) AND (btrim((COALESCE(failure_reason, ''::character varying))::text) <> ''::text)))),
    CONSTRAINT kb_knowledge_index_rebuild_generation_check CHECK ((generation > 0)),
    CONSTRAINT kb_knowledge_index_rebuild_index_version_check CHECK ((btrim((index_version)::text) <> ''::text)),
    CONSTRAINT kb_knowledge_index_rebuild_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying])::text[]))),
    CONSTRAINT kb_knowledge_index_rebuild_target_embedding_model_check CHECK ((btrim((target_embedding_model)::text) <> ''::text))
);


--
-- Name: TABLE kb_knowledge_index_rebuild; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.kb_knowledge_index_rebuild IS '知识库索引冻结 cohort；只有 desired/latest ACTIVE generation 可原子激活';


--
-- Name: kb_knowledge_index_rebuild_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.kb_knowledge_index_rebuild_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: kb_knowledge_index_rebuild_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.kb_knowledge_index_rebuild_id_seq OWNED BY public.kb_knowledge_index_rebuild.id;


--
-- Name: kb_knowledge_index_rebuild_member; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.kb_knowledge_index_rebuild_member (
    tenant_id bigint NOT NULL,
    rebuild_id bigint NOT NULL,
    knowledge_base_id bigint NOT NULL,
    document_id bigint NOT NULL,
    document_updated_at timestamp with time zone NOT NULL,
    expected_chunk_count integer NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT kb_knowledge_index_rebuild_member_expected_chunk_count_check CHECK ((expected_chunk_count > 0))
);


--
-- Name: TABLE kb_knowledge_index_rebuild_member; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.kb_knowledge_index_rebuild_member IS '重建计划的冻结文档成员与切片数量快照；并发新文档进入下一 generation';


--
-- Name: kb_model_usage_policy_event_receipt; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.kb_model_usage_policy_event_receipt (
    event_id character varying(128) NOT NULL,
    event_version bigint NOT NULL,
    occurred_at timestamp with time zone NOT NULL,
    consumed_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_kb_model_usage_policy_event_receipt_version CHECK ((event_version = ANY (ARRAY[(1)::bigint, (2)::bigint])))
);


--
-- Name: kb_model_usage_policy_projection; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.kb_model_usage_policy_projection (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    model_config_id character varying(64) NOT NULL,
    provider_code character varying(64) NOT NULL,
    provider_kind character varying(64) NOT NULL,
    provider_endpoint character varying(512) NOT NULL,
    auth_type character varying(32) NOT NULL,
    secret_ref_id character varying(64),
    secret_masked_value character varying(128),
    model_code character varying(128) NOT NULL,
    capabilities text NOT NULL,
    allowed_use_cases text NOT NULL,
    allowed_knowledge_base_ids text DEFAULT ''::text NOT NULL,
    effective_options_json text DEFAULT '{}'::text NOT NULL,
    max_input_tokens integer DEFAULT 0 NOT NULL,
    max_output_tokens integer DEFAULT 0 NOT NULL,
    policy_version character varying(128) NOT NULL,
    enabled boolean DEFAULT false NOT NULL,
    expires_at timestamp with time zone,
    source_event_id character varying(128) NOT NULL,
    source_event_version bigint DEFAULT 1 NOT NULL,
    last_event_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    version integer DEFAULT 0 NOT NULL,
    provider_id bigint NOT NULL,
    model_config_version integer NOT NULL,
    model_type character varying(32) NOT NULL,
    effective_options_digest character varying(64) NOT NULL,
    policy_revision bigint NOT NULL,
    policy_digest character varying(64) NOT NULL,
    CONSTRAINT ck_kb_model_usage_policy_config_version CHECK ((model_config_version >= 0)),
    CONSTRAINT ck_kb_model_usage_policy_digest CHECK (((policy_digest)::text ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT ck_kb_model_usage_policy_model_type CHECK (((btrim((model_type)::text) <> ''::text) AND ((model_type)::text = upper((model_type)::text)))),
    CONSTRAINT ck_kb_model_usage_policy_options_digest CHECK (((effective_options_digest)::text ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT ck_kb_model_usage_policy_revision CHECK (((policy_revision > 0) AND (policy_revision < '9223372036854775807'::bigint)))
);


--
-- Name: COLUMN kb_model_usage_policy_projection.provider_endpoint; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_model_usage_policy_projection.provider_endpoint IS '模型供应商访问地址，来自 model-hub 运行时连接投影';


--
-- Name: COLUMN kb_model_usage_policy_projection.auth_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_model_usage_policy_projection.auth_type IS '模型供应商鉴权方式，如 api_key、no_auth、aws_credentials、gcp_service_account';


--
-- Name: COLUMN kb_model_usage_policy_projection.secret_ref_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_model_usage_policy_projection.secret_ref_id IS '模型密钥引用 ID，不保存 API Key 或云凭证明文；credential-material 供应商必填，no_auth 供应商为空';


--
-- Name: COLUMN kb_model_usage_policy_projection.secret_masked_value; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_model_usage_policy_projection.secret_masked_value IS '模型密钥脱敏展示值，不用于运行时鉴权；credential-material 供应商必填，no_auth 供应商为空';


--
-- Name: COLUMN kb_model_usage_policy_projection.provider_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_model_usage_policy_projection.provider_id IS '权威模型供应商 ID；运行时凭证解析必须按此绑定';


--
-- Name: COLUMN kb_model_usage_policy_projection.model_config_version; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_model_usage_policy_projection.model_config_version IS '权威模型配置乐观锁版本；与当前 mdl_config.version 不一致时禁止运行时调用';


--
-- Name: COLUMN kb_model_usage_policy_projection.model_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_model_usage_policy_projection.model_type IS '权威模型类型；与当前 mdl_config.model_type 不一致时禁止运行时调用';


--
-- Name: COLUMN kb_model_usage_policy_projection.effective_options_digest; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_model_usage_policy_projection.effective_options_digest IS 'PostgreSQL JSONB canonical effective options SHA-256；不一致时禁止解密和出网';


--
-- Name: COLUMN kb_model_usage_policy_projection.policy_revision; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_model_usage_policy_projection.policy_revision IS 'model-hub 持久化单调策略修订号；不使用毫秒时间戳排序';


--
-- Name: COLUMN kb_model_usage_policy_projection.policy_digest; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.kb_model_usage_policy_projection.policy_digest IS '同一策略修订号的 SHA-256 载荷摘要；摘要冲突必须 fail-closed';


--
-- Name: kb_model_usage_policy_projection_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.kb_model_usage_policy_projection_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: kb_model_usage_policy_projection_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.kb_model_usage_policy_projection_id_seq OWNED BY public.kb_model_usage_policy_projection.id;


--
-- Name: mdl_approval_decision_inbox; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.mdl_approval_decision_inbox (
    event_id character varying(255) NOT NULL,
    event_type character varying(128) NOT NULL,
    tenant_id bigint NOT NULL,
    task_id bigint NOT NULL,
    source_resource_type character varying(64) NOT NULL,
    source_resource_id bigint NOT NULL,
    trace_id character varying(128) NOT NULL,
    occurred_at timestamp with time zone NOT NULL,
    processed_at timestamp with time zone DEFAULT now() NOT NULL,
    approval_request_id character varying(128) NOT NULL,
    CONSTRAINT ck_mdl_approval_decision_inbox_event CHECK (((event_type)::text = 'ApprovalDecisionRecorded'::text)),
    CONSTRAINT ck_mdl_approval_decision_inbox_request_not_blank CHECK ((btrim((approval_request_id)::text) <> ''::text))
);


--
-- Name: mdl_approval_request_outbox; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.mdl_approval_request_outbox (
    id bigint NOT NULL,
    event_id character varying(128) NOT NULL,
    event_type character varying(64) NOT NULL,
    tenant_id character varying(64) NOT NULL,
    aggregate_type character varying(64) NOT NULL,
    aggregate_id character varying(64) NOT NULL,
    event_version bigint NOT NULL,
    schema_version character varying(32) NOT NULL,
    occurred_at timestamp with time zone NOT NULL,
    trace_id character varying(128) NOT NULL,
    producer character varying(64) NOT NULL,
    payload_json jsonb NOT NULL,
    status character varying(16) DEFAULT 'PENDING'::character varying NOT NULL,
    attempts integer DEFAULT 0 NOT NULL,
    next_attempt_at timestamp with time zone DEFAULT now() NOT NULL,
    lease_owner character varying(128),
    lease_until timestamp with time zone,
    published_at timestamp with time zone,
    last_error character varying(128),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_mdl_approval_request_outbox_attempts CHECK ((attempts >= 0)),
    CONSTRAINT ck_mdl_approval_request_outbox_lease CHECK (((((status)::text = 'IN_FLIGHT'::text) AND (lease_owner IS NOT NULL) AND (lease_until IS NOT NULL)) OR (((status)::text <> 'IN_FLIGHT'::text) AND (lease_owner IS NULL) AND (lease_until IS NULL)))),
    CONSTRAINT ck_mdl_approval_request_outbox_protocol CHECK (COALESCE(((((event_type)::text = 'ModelConfigApprovalRequested'::text) AND ((aggregate_type)::text = ANY ((ARRAY['ModelProvider'::character varying, 'ModelConfig'::character varying])::text[])) AND ((producer)::text = 'model-hub'::text) AND (event_version = 1) AND ((schema_version)::text = ANY ((ARRAY['v1'::character varying, 'v2'::character varying])::text[])) AND ((payload_json ->> 'approvalRequestId'::text) IS NOT NULL) AND (btrim((payload_json ->> 'approvalRequestId'::text)) <> ''::text) AND ((payload_json ->> 'providerId'::text) IS NOT NULL) AND (btrim((payload_json ->> 'providerId'::text)) <> ''::text) AND ((payload_json ->> 'changeSummary'::text) IS NOT NULL) AND (btrim((payload_json ->> 'changeSummary'::text)) <> ''::text) AND ((payload_json ->> 'riskLevel'::text) IS NOT NULL) AND (btrim((payload_json ->> 'riskLevel'::text)) <> ''::text) AND ((payload_json ->> 'operatorId'::text) IS NOT NULL) AND (btrim((payload_json ->> 'operatorId'::text)) <> ''::text) AND ((((aggregate_type)::text = 'ModelProvider'::text) AND ((payload_json ->> 'approvalType'::text) = 'model_provider_change'::text) AND ((payload_json ->> 'sourceResourceType'::text) = 'model_provider'::text) AND ((payload_json ->> 'sourceResourceId'::text) = (aggregate_id)::text) AND ((payload_json ->> 'providerId'::text) = (aggregate_id)::text) AND ((NOT (payload_json ? 'modelConfigId'::text)) OR ((payload_json -> 'modelConfigId'::text) = 'null'::jsonb))) OR (((aggregate_type)::text = 'ModelConfig'::text) AND ((payload_json ->> 'approvalType'::text) = 'model_config_change'::text) AND ((payload_json ->> 'sourceResourceType'::text) = 'model_config'::text) AND ((payload_json ->> 'sourceResourceId'::text) = (aggregate_id)::text) AND ((payload_json ->> 'modelConfigId'::text) = (aggregate_id)::text))) AND ((((schema_version)::text = 'v1'::text) AND ((status)::text = 'PUBLISHED'::text)) OR (((schema_version)::text = 'v2'::text) AND ((payload_json ->> 'evidenceSchema'::text) = 'model-approval-evidence/v1'::text) AND ((payload_json ->> 'evidenceDigest'::text) ~ '^[0-9a-f]{64}$'::text) AND ((payload_json ->> 'targetDigest'::text) ~ '^[0-9a-f]{64}$'::text) AND (jsonb_typeof((payload_json -> 'evidence'::text)) = 'object'::text) AND (((payload_json -> 'evidence'::text) ->> 'tenantId'::text) = (tenant_id)::text) AND (((payload_json -> 'evidence'::text) ->> 'approvalRequestId'::text) = (payload_json ->> 'approvalRequestId'::text)) AND (((payload_json -> 'evidence'::text) ->> 'approvalType'::text) = (payload_json ->> 'approvalType'::text)) AND (((payload_json -> 'evidence'::text) ->> 'sourceContext'::text) = 'model-hub'::text) AND (((payload_json -> 'evidence'::text) ->> 'resourceType'::text) = (payload_json ->> 'sourceResourceType'::text)) AND (((payload_json -> 'evidence'::text) ->> 'resourceId'::text) = (aggregate_id)::text) AND (((payload_json -> 'evidence'::text) ->> 'providerId'::text) = (payload_json ->> 'providerId'::text)) AND (((payload_json -> 'evidence'::text) ->> 'requesterId'::text) = (payload_json ->> 'operatorId'::text)) AND (((payload_json -> 'evidence'::text) ->> 'riskLevel'::text) = (payload_json ->> 'riskLevel'::text))))) OR (((event_type)::text = 'ModelApprovalCancellationRequested'::text) AND ((aggregate_type)::text = ANY ((ARRAY['ModelProvider'::character varying, 'ModelConfig'::character varying])::text[])) AND ((producer)::text = 'model-hub'::text) AND (event_version = 1) AND ((schema_version)::text = 'v1'::text) AND (jsonb_typeof(payload_json) = 'object'::text) AND ((payload_json - ARRAY['approvalRequestId'::text, 'sourceContext'::text, 'sourceResourceType'::text, 'sourceResourceId'::text, 'operatorId'::text, 'reason'::text, 'requestedAt'::text]) = '{}'::jsonb) AND (btrim((payload_json ->> 'approvalRequestId'::text)) <> ''::text) AND ((payload_json ->> 'sourceContext'::text) = 'model-hub'::text) AND (btrim((payload_json ->> 'operatorId'::text)) <> ''::text) AND (btrim((payload_json ->> 'reason'::text)) <> ''::text) AND (char_length((payload_json ->> 'reason'::text)) <= 500) AND ((payload_json ->> 'reason'::text) !~ '[[:cntrl:]]'::text) AND (btrim((payload_json ->> 'requestedAt'::text)) <> ''::text) AND ((event_id)::text = ((((('ModelApprovalCancellationRequested:'::text || (tenant_id)::text) || ':'::text) || (payload_json ->> 'sourceResourceType'::text)) || ':'::text) || (payload_json ->> 'approvalRequestId'::text))) AND ((payload_json ->> 'sourceResourceId'::text) = (aggregate_id)::text) AND ((((aggregate_type)::text = 'ModelProvider'::text) AND ((payload_json ->> 'sourceResourceType'::text) = 'model_provider'::text)) OR (((aggregate_type)::text = 'ModelConfig'::text) AND ((payload_json ->> 'sourceResourceType'::text) = 'model_config'::text))))), false)),
    CONSTRAINT ck_mdl_approval_request_outbox_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'IN_FLIGHT'::character varying, 'PUBLISHED'::character varying])::text[])))
);


--
-- Name: mdl_approval_request_outbox_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.mdl_approval_request_outbox_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: mdl_approval_request_outbox_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.mdl_approval_request_outbox_id_seq OWNED BY public.mdl_approval_request_outbox.id;


--
-- Name: mdl_config; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.mdl_config (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    provider_id bigint NOT NULL,
    model_code character varying(128) NOT NULL,
    model_name character varying(128) NOT NULL,
    model_type character varying(32) NOT NULL,
    context_window integer NOT NULL,
    embedding_dimension integer,
    input_price_per_1k numeric(18,8) NOT NULL,
    output_price_per_1k numeric(18,8) NOT NULL,
    options_json text NOT NULL,
    connectivity_status character varying(32) DEFAULT 'NOT_TESTED'::character varying NOT NULL,
    connectivity_checked_at timestamp with time zone,
    connectivity_message character varying(512),
    status character varying(32) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by bigint,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_by bigint,
    deleted_at timestamp with time zone,
    version integer DEFAULT 0 NOT NULL,
    approval_request_id character varying(128),
    approval_evidence_schema character varying(64),
    approval_evidence_digest character varying(64),
    approval_target_digest character varying(64),
    CONSTRAINT ck_mdl_config_approval_evidence CHECK (COALESCE((((approval_evidence_schema IS NULL) AND (approval_evidence_digest IS NULL) AND (approval_target_digest IS NULL) AND ((status)::text <> 'PENDING_APPROVAL'::text)) OR (((approval_evidence_schema)::text = 'model-approval-evidence/v1'::text) AND ((approval_evidence_digest)::text ~ '^[0-9a-f]{64}$'::text) AND ((approval_target_digest)::text ~ '^[0-9a-f]{64}$'::text) AND ((status)::text = 'PENDING_APPROVAL'::text))), false)),
    CONSTRAINT ck_mdl_config_approval_request_id_not_blank CHECK (((approval_request_id IS NULL) OR (btrim((approval_request_id)::text) <> ''::text))),
    CONSTRAINT ck_mdl_config_pending_approval_request CHECK ((((status)::text <> 'PENDING_APPROVAL'::text) OR (approval_request_id IS NOT NULL)))
);


--
-- Name: mdl_config_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.mdl_config_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: mdl_config_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.mdl_config_id_seq OWNED BY public.mdl_config.id;


--
-- Name: mdl_creation_request; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.mdl_creation_request (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    resource_type character varying(32) NOT NULL,
    request_id character varying(128) NOT NULL,
    request_digest character varying(64) NOT NULL,
    result_parent_resource_id bigint,
    result_resource_id bigint,
    result_resource_code character varying(128),
    result_model_type character varying(32),
    result_status character varying(32),
    result_approval_request_id character varying(128),
    result_connectivity_status character varying(32),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    completed_at timestamp with time zone,
    CONSTRAINT ck_mdl_creation_request_completion CHECK ((((completed_at IS NULL) AND (result_parent_resource_id IS NULL) AND (result_resource_id IS NULL) AND (result_resource_code IS NULL) AND (result_model_type IS NULL) AND (result_status IS NULL) AND (result_approval_request_id IS NULL) AND (result_connectivity_status IS NULL)) OR ((completed_at IS NOT NULL) AND (result_resource_id IS NOT NULL) AND (result_status IS NOT NULL) AND (btrim((result_status)::text) <> ''::text) AND ((((resource_type)::text = 'MODEL_PROVIDER'::text) AND (result_parent_resource_id IS NULL) AND (result_resource_code IS NULL) AND (result_model_type IS NULL) AND (result_approval_request_id IS NOT NULL) AND (btrim((result_approval_request_id)::text) <> ''::text) AND (result_connectivity_status IS NULL)) OR (((resource_type)::text = 'MODEL_CONFIG'::text) AND (result_parent_resource_id IS NOT NULL) AND (result_resource_code IS NOT NULL) AND (btrim((result_resource_code)::text) <> ''::text) AND (result_model_type IS NOT NULL) AND (btrim((result_model_type)::text) <> ''::text) AND ((result_approval_request_id IS NULL) OR (btrim((result_approval_request_id)::text) <> ''::text)) AND (((result_status)::text <> 'PENDING_APPROVAL'::text) OR (result_approval_request_id IS NOT NULL)) AND (result_connectivity_status IS NOT NULL) AND (btrim((result_connectivity_status)::text) <> ''::text)))))),
    CONSTRAINT ck_mdl_creation_request_digest CHECK (((request_digest)::text ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT ck_mdl_creation_request_request_id CHECK (((request_id)::text ~ '^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$'::text)),
    CONSTRAINT ck_mdl_creation_request_resource_type CHECK (((resource_type)::text = ANY ((ARRAY['MODEL_PROVIDER'::character varying, 'MODEL_CONFIG'::character varying])::text[]))),
    CONSTRAINT ck_mdl_creation_request_result_id CHECK ((((result_parent_resource_id IS NULL) OR (result_parent_resource_id > 0)) AND ((result_resource_id IS NULL) OR (result_resource_id > 0))))
);


--
-- Name: TABLE mdl_creation_request; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.mdl_creation_request IS 'Tenant-scoped idempotency claim and completed resource binding for ModelHub create commands.';


--
-- Name: COLUMN mdl_creation_request.request_digest; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.mdl_creation_request.request_digest IS 'SHA-256 of the normalized command; provider credentials contribute only an irreversible fingerprint.';


--
-- Name: COLUMN mdl_creation_request.result_parent_resource_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.mdl_creation_request.result_parent_resource_id IS 'Provider ID returned with a model-config response; null for provider creation.';


--
-- Name: COLUMN mdl_creation_request.result_resource_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.mdl_creation_request.result_resource_id IS 'Provider or model-config ID in the non-secret API response snapshot; completed in the creation transaction.';


--
-- Name: COLUMN mdl_creation_request.result_resource_code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.mdl_creation_request.result_resource_code IS 'Public model code returned with a model-config response; never command or credential payload.';


--
-- Name: COLUMN mdl_creation_request.result_status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.mdl_creation_request.result_status IS 'Exact public resource status returned by the successful create response.';


--
-- Name: COLUMN mdl_creation_request.result_approval_request_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.mdl_creation_request.result_approval_request_id IS 'Public approval correlation returned by the successful create response when present.';


--
-- Name: COLUMN mdl_creation_request.result_connectivity_status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.mdl_creation_request.result_connectivity_status IS 'Exact public connectivity status returned by a model-config create response.';


--
-- Name: mdl_creation_request_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.mdl_creation_request_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: mdl_creation_request_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.mdl_creation_request_id_seq OWNED BY public.mdl_creation_request.id;


--
-- Name: mdl_invocation_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.mdl_invocation_log (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    trace_id character varying(64) NOT NULL,
    source_context character varying(64) NOT NULL,
    source_resource_type character varying(64),
    source_resource_id bigint,
    provider_id bigint,
    model_config_id bigint,
    request_hash character varying(128),
    input_tokens integer DEFAULT 0 NOT NULL,
    output_tokens integer DEFAULT 0 NOT NULL,
    total_cost numeric(18,8) DEFAULT 0 NOT NULL,
    latency_ms integer,
    status character varying(32) NOT NULL,
    error_code character varying(64),
    error_message text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    idempotency_key character varying(128) NOT NULL
);


--
-- Name: COLUMN mdl_invocation_log.idempotency_key; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.mdl_invocation_log.idempotency_key IS 'Stable App Studio client message identifier (or legacy safe identifier); provider exactly-once is not implied';


--
-- Name: mdl_invocation_log_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.mdl_invocation_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: mdl_invocation_log_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.mdl_invocation_log_id_seq OWNED BY public.mdl_invocation_log.id;


--
-- Name: mdl_model_usage_policy_outbox; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.mdl_model_usage_policy_outbox (
    id bigint NOT NULL,
    event_id character varying(128) NOT NULL,
    event_type character varying(64) NOT NULL,
    tenant_id character varying(64) NOT NULL,
    aggregate_type character varying(64) NOT NULL,
    aggregate_id character varying(64) NOT NULL,
    event_version bigint NOT NULL,
    schema_version character varying(32) NOT NULL,
    occurred_at timestamp with time zone NOT NULL,
    trace_id character varying(128) NOT NULL,
    producer character varying(64) NOT NULL,
    payload_json jsonb NOT NULL,
    status character varying(16) DEFAULT 'PENDING'::character varying NOT NULL,
    attempts integer DEFAULT 0 NOT NULL,
    next_attempt_at timestamp with time zone DEFAULT now() NOT NULL,
    lease_owner character varying(128),
    lease_until timestamp with time zone,
    published_at timestamp with time zone,
    last_error character varying(128),
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_mdl_model_usage_policy_outbox_attempts CHECK ((attempts >= 0)),
    CONSTRAINT ck_mdl_model_usage_policy_outbox_lease CHECK (((((status)::text = 'IN_FLIGHT'::text) AND (lease_owner IS NOT NULL) AND (lease_until IS NOT NULL)) OR (((status)::text <> 'IN_FLIGHT'::text) AND (lease_owner IS NULL) AND (lease_until IS NULL)))),
    CONSTRAINT ck_mdl_model_usage_policy_outbox_protocol CHECK ((((event_type)::text = 'ModelUsagePolicyPublished'::text) AND ((aggregate_type)::text = 'ModelConfig'::text) AND ((producer)::text = 'model-hub'::text) AND (((event_version = 1) AND ((schema_version)::text = 'v1'::text)) OR ((event_version = 2) AND ((schema_version)::text = 'schema/model-usage-policy.v2'::text))))),
    CONSTRAINT ck_mdl_model_usage_policy_outbox_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'IN_FLIGHT'::character varying, 'PUBLISHED'::character varying])::text[])))
);


--
-- Name: mdl_model_usage_policy_outbox_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.mdl_model_usage_policy_outbox_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: mdl_model_usage_policy_outbox_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.mdl_model_usage_policy_outbox_id_seq OWNED BY public.mdl_model_usage_policy_outbox.id;


--
-- Name: mdl_model_usage_policy_revision_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.mdl_model_usage_policy_revision_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    MAXVALUE 9223372036854775806
    CACHE 1;


--
-- Name: mdl_provider; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.mdl_provider (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    provider_code character varying(64) NOT NULL,
    provider_name character varying(128) NOT NULL,
    base_url character varying(512) NOT NULL,
    auth_type character varying(32) NOT NULL,
    status character varying(32) NOT NULL,
    health_checked_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by bigint,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_by bigint,
    deleted_at timestamp with time zone,
    version integer DEFAULT 0 NOT NULL,
    approval_request_id character varying(128),
    approval_evidence_schema character varying(64),
    approval_evidence_digest character varying(64),
    approval_target_digest character varying(64),
    CONSTRAINT ck_mdl_provider_approval_evidence CHECK (COALESCE((((approval_evidence_schema IS NULL) AND (approval_evidence_digest IS NULL) AND (approval_target_digest IS NULL) AND ((status)::text <> 'PENDING_APPROVAL'::text)) OR (((approval_evidence_schema)::text = 'model-approval-evidence/v1'::text) AND ((approval_evidence_digest)::text ~ '^[0-9a-f]{64}$'::text) AND ((approval_target_digest)::text ~ '^[0-9a-f]{64}$'::text) AND ((status)::text = 'PENDING_APPROVAL'::text))), false)),
    CONSTRAINT ck_mdl_provider_approval_request_id_not_blank CHECK (((approval_request_id IS NULL) OR (btrim((approval_request_id)::text) <> ''::text))),
    CONSTRAINT ck_mdl_provider_pending_approval_request CHECK ((((status)::text <> 'PENDING_APPROVAL'::text) OR (approval_request_id IS NOT NULL)))
);


--
-- Name: mdl_provider_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.mdl_provider_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: mdl_provider_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.mdl_provider_id_seq OWNED BY public.mdl_provider.id;


--
-- Name: mdl_secret; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.mdl_secret (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    provider_id bigint NOT NULL,
    secret_name character varying(128) NOT NULL,
    secret_ciphertext text NOT NULL,
    secret_digest character varying(128) NOT NULL,
    secret_masked_value character varying(128) NOT NULL,
    kms_key_id character varying(128) NOT NULL,
    status character varying(32) NOT NULL,
    rotated_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by bigint,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_by bigint,
    deleted_at timestamp with time zone,
    version integer DEFAULT 0 NOT NULL,
    cipher_algorithm character varying(32) DEFAULT 'AES-256-GCM'::character varying NOT NULL,
    cipher_version smallint DEFAULT 1 NOT NULL,
    CONSTRAINT ck_mdl_secret_cipher_algorithm CHECK (((cipher_algorithm)::text = 'AES-256-GCM'::text)),
    CONSTRAINT ck_mdl_secret_cipher_version CHECK ((cipher_version = ANY (ARRAY[1, 2]))),
    CONSTRAINT ck_mdl_secret_ciphertext_envelope CHECK ((((cipher_version = 1) AND (secret_ciphertext ~~ 'aes-gcm:v1:%'::text)) OR ((cipher_version = 2) AND (secret_ciphertext ~~ 'aip-secret:v2:AES-256-GCM:%'::text))))
);


--
-- Name: COLUMN mdl_secret.secret_masked_value; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.mdl_secret.secret_masked_value IS '凭证材料脱敏展示值，供策略事件和本地投影传播，禁止使用固定占位符伪造';


--
-- Name: COLUMN mdl_secret.cipher_algorithm; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.mdl_secret.cipher_algorithm IS '凭证 envelope 算法；当前仅允许 AES-256-GCM';


--
-- Name: COLUMN mdl_secret.cipher_version; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.mdl_secret.cipher_version IS '凭证 envelope 版本；1 为历史单 key 格式，2 为带 key ID 与 AAD 的可轮换格式';


--
-- Name: mdl_secret_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.mdl_secret_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: mdl_secret_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.mdl_secret_id_seq OWNED BY public.mdl_secret.id;


--
-- Name: rag_authorization_decision_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.rag_authorization_decision_log (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    actor_id bigint NOT NULL,
    resource_type character varying(64) NOT NULL,
    resource_id character varying(128) NOT NULL,
    decision character varying(32) NOT NULL,
    reason_code character varying(128) NOT NULL,
    trace_id character varying(128),
    details_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_rag_authorization_decision_log_decision CHECK (((decision)::text = ANY ((ARRAY['ALLOW'::character varying, 'DENY'::character varying, 'FILTERED'::character varying, 'MASKED'::character varying])::text[])))
);


--
-- Name: TABLE rag_authorization_decision_log; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.rag_authorization_decision_log IS 'RAG 授权决策摘要，支撑 fail-closed 审计和排障';


--
-- Name: rag_authorization_decision_log_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.rag_authorization_decision_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: rag_authorization_decision_log_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.rag_authorization_decision_log_id_seq OWNED BY public.rag_authorization_decision_log.id;


--
-- Name: summary_metric_snapshot; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.summary_metric_snapshot (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    scope_type character varying(32) NOT NULL,
    metric_code character varying(128) NOT NULL,
    metric_payload jsonb DEFAULT '{}'::jsonb NOT NULL,
    freshness character varying(32) NOT NULL,
    partial boolean DEFAULT false NOT NULL,
    failed_reason character varying(512),
    as_of timestamp with time zone NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_summary_metric_snapshot_freshness CHECK (((freshness)::text = ANY ((ARRAY['REALTIME'::character varying, 'ONE_MINUTE'::character varying, 'FIVE_MINUTES'::character varying])::text[]))),
    CONSTRAINT ck_summary_metric_snapshot_scope CHECK (((scope_type)::text = ANY ((ARRAY['PLATFORM'::character varying, 'TENANT'::character varying])::text[])))
);


--
-- Name: TABLE summary_metric_snapshot; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.summary_metric_snapshot IS 'SummaryOps 指标快照，支撑平台总览和租户工作台';


--
-- Name: summary_metric_snapshot_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.summary_metric_snapshot_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: summary_metric_snapshot_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.summary_metric_snapshot_id_seq OWNED BY public.summary_metric_snapshot.id;


--
-- Name: app_application id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_application ALTER COLUMN id SET DEFAULT nextval('public.app_application_id_seq'::regclass);


--
-- Name: app_application_release id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_application_release ALTER COLUMN id SET DEFAULT nextval('public.app_application_release_id_seq'::regclass);


--
-- Name: app_application_release_outbox id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_application_release_outbox ALTER COLUMN id SET DEFAULT nextval('public.app_application_release_outbox_id_seq'::regclass);


--
-- Name: app_bound_knowledge_base id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_bound_knowledge_base ALTER COLUMN id SET DEFAULT nextval('public.app_bound_knowledge_base_id_seq'::regclass);


--
-- Name: app_chat_message id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_chat_message ALTER COLUMN id SET DEFAULT nextval('public.app_chat_message_id_seq'::regclass);


--
-- Name: app_chat_message_feedback id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_chat_message_feedback ALTER COLUMN id SET DEFAULT nextval('public.app_chat_message_feedback_id_seq'::regclass);


--
-- Name: app_chat_retrieval_evidence id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_chat_retrieval_evidence ALTER COLUMN id SET DEFAULT nextval('public.app_chat_retrieval_evidence_id_seq'::regclass);


--
-- Name: app_chat_run run_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_chat_run ALTER COLUMN run_id SET DEFAULT nextval('public.app_chat_run_run_id_seq'::regclass);


--
-- Name: app_chat_run_replay_request request_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_chat_run_replay_request ALTER COLUMN request_id SET DEFAULT nextval('public.app_chat_run_replay_request_request_id_seq'::regclass);


--
-- Name: app_chat_session id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_chat_session ALTER COLUMN id SET DEFAULT nextval('public.app_chat_session_id_seq'::regclass);


--
-- Name: app_model_usage_policy_projection id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_model_usage_policy_projection ALTER COLUMN id SET DEFAULT nextval('public.app_model_usage_policy_projection_id_seq'::regclass);


--
-- Name: app_publish_preflight_result id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_publish_preflight_result ALTER COLUMN id SET DEFAULT nextval('public.app_publish_preflight_result_id_seq'::regclass);


--
-- Name: appr_decision_outbox id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appr_decision_outbox ALTER COLUMN id SET DEFAULT nextval('public.appr_decision_outbox_id_seq'::regclass);


--
-- Name: appr_model_approval_cancellation id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appr_model_approval_cancellation ALTER COLUMN id SET DEFAULT nextval('public.appr_model_approval_cancellation_id_seq'::regclass);


--
-- Name: appr_record id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appr_record ALTER COLUMN id SET DEFAULT nextval('public.appr_record_id_seq'::regclass);


--
-- Name: appr_task id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appr_task ALTER COLUMN id SET DEFAULT nextval('public.appr_task_id_seq'::regclass);


--
-- Name: audit_operation_log id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_operation_log ALTER COLUMN id SET DEFAULT nextval('public.audit_operation_log_id_seq'::regclass);


--
-- Name: audit_security_log id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_security_log ALTER COLUMN id SET DEFAULT nextval('public.audit_security_log_id_seq'::regclass);


--
-- Name: iam_delegated_tenant_action id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_delegated_tenant_action ALTER COLUMN id SET DEFAULT nextval('public.iam_delegated_tenant_action_id_seq'::regclass);


--
-- Name: iam_department id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_department ALTER COLUMN id SET DEFAULT nextval('public.iam_department_id_seq'::regclass);


--
-- Name: iam_menu id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_menu ALTER COLUMN id SET DEFAULT nextval('public.iam_menu_id_seq'::regclass);


--
-- Name: iam_menu_feature id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_menu_feature ALTER COLUMN id SET DEFAULT nextval('public.iam_menu_feature_id_seq'::regclass);


--
-- Name: iam_notification_outbox id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_notification_outbox ALTER COLUMN id SET DEFAULT nextval('public.iam_notification_outbox_id_seq'::regclass);


--
-- Name: iam_permission id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_permission ALTER COLUMN id SET DEFAULT nextval('public.iam_permission_id_seq'::regclass);


--
-- Name: iam_permission_request id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_permission_request ALTER COLUMN id SET DEFAULT nextval('public.iam_permission_request_id_seq'::regclass);


--
-- Name: iam_resource_acl id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_resource_acl ALTER COLUMN id SET DEFAULT nextval('public.iam_resource_acl_id_seq'::regclass);


--
-- Name: iam_role id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_role ALTER COLUMN id SET DEFAULT nextval('public.iam_role_id_seq'::regclass);


--
-- Name: iam_role_menu id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_role_menu ALTER COLUMN id SET DEFAULT nextval('public.iam_role_menu_id_seq'::regclass);


--
-- Name: iam_role_permission id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_role_permission ALTER COLUMN id SET DEFAULT nextval('public.iam_role_permission_id_seq'::regclass);


--
-- Name: iam_tenant id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_tenant ALTER COLUMN id SET DEFAULT nextval('public.iam_tenant_id_seq'::regclass);


--
-- Name: iam_tenant_feature id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_tenant_feature ALTER COLUMN id SET DEFAULT nextval('public.iam_tenant_feature_id_seq'::regclass);


--
-- Name: iam_user id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_user ALTER COLUMN id SET DEFAULT nextval('public.iam_user_id_seq'::regclass);


--
-- Name: iam_user_role id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_user_role ALTER COLUMN id SET DEFAULT nextval('public.iam_user_role_id_seq'::regclass);


--
-- Name: kb_citation_record id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_citation_record ALTER COLUMN id SET DEFAULT nextval('public.kb_citation_record_id_seq'::regclass);


--
-- Name: kb_document id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_document ALTER COLUMN id SET DEFAULT nextval('public.kb_document_id_seq'::regclass);


--
-- Name: kb_document_chunk id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_document_chunk ALTER COLUMN id SET DEFAULT nextval('public.kb_document_chunk_id_seq'::regclass);


--
-- Name: kb_document_object_cleanup id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_document_object_cleanup ALTER COLUMN id SET DEFAULT nextval('public.kb_document_object_cleanup_id_seq'::regclass);


--
-- Name: kb_embedding_job id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_embedding_job ALTER COLUMN id SET DEFAULT nextval('public.kb_embedding_job_id_seq'::regclass);


--
-- Name: kb_knowledge_base id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_knowledge_base ALTER COLUMN id SET DEFAULT nextval('public.kb_knowledge_base_id_seq'::regclass);


--
-- Name: kb_knowledge_index_rebuild id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_knowledge_index_rebuild ALTER COLUMN id SET DEFAULT nextval('public.kb_knowledge_index_rebuild_id_seq'::regclass);


--
-- Name: kb_model_usage_policy_projection id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_model_usage_policy_projection ALTER COLUMN id SET DEFAULT nextval('public.kb_model_usage_policy_projection_id_seq'::regclass);


--
-- Name: mdl_approval_request_outbox id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mdl_approval_request_outbox ALTER COLUMN id SET DEFAULT nextval('public.mdl_approval_request_outbox_id_seq'::regclass);


--
-- Name: mdl_config id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mdl_config ALTER COLUMN id SET DEFAULT nextval('public.mdl_config_id_seq'::regclass);


--
-- Name: mdl_creation_request id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mdl_creation_request ALTER COLUMN id SET DEFAULT nextval('public.mdl_creation_request_id_seq'::regclass);


--
-- Name: mdl_invocation_log id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mdl_invocation_log ALTER COLUMN id SET DEFAULT nextval('public.mdl_invocation_log_id_seq'::regclass);


--
-- Name: mdl_model_usage_policy_outbox id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mdl_model_usage_policy_outbox ALTER COLUMN id SET DEFAULT nextval('public.mdl_model_usage_policy_outbox_id_seq'::regclass);


--
-- Name: mdl_provider id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mdl_provider ALTER COLUMN id SET DEFAULT nextval('public.mdl_provider_id_seq'::regclass);


--
-- Name: mdl_secret id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mdl_secret ALTER COLUMN id SET DEFAULT nextval('public.mdl_secret_id_seq'::regclass);


--
-- Name: rag_authorization_decision_log id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rag_authorization_decision_log ALTER COLUMN id SET DEFAULT nextval('public.rag_authorization_decision_log_id_seq'::regclass);


--
-- Name: summary_metric_snapshot id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.summary_metric_snapshot ALTER COLUMN id SET DEFAULT nextval('public.summary_metric_snapshot_id_seq'::regclass);


--
-- Name: app_application_active_release app_application_active_release_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_application_active_release
    ADD CONSTRAINT app_application_active_release_pkey PRIMARY KEY (tenant_id, application_id);


--
-- Name: app_application app_application_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_application
    ADD CONSTRAINT app_application_pkey PRIMARY KEY (id);


--
-- Name: app_application_release_outbox app_application_release_outbox_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_application_release_outbox
    ADD CONSTRAINT app_application_release_outbox_pkey PRIMARY KEY (id);


--
-- Name: app_application_release app_application_release_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_application_release
    ADD CONSTRAINT app_application_release_pkey PRIMARY KEY (id);


--
-- Name: app_bound_knowledge_base app_bound_knowledge_base_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_bound_knowledge_base
    ADD CONSTRAINT app_bound_knowledge_base_pkey PRIMARY KEY (id);


--
-- Name: app_chat_message_feedback app_chat_message_feedback_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_chat_message_feedback
    ADD CONSTRAINT app_chat_message_feedback_pkey PRIMARY KEY (id);


--
-- Name: app_chat_message app_chat_message_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_chat_message
    ADD CONSTRAINT app_chat_message_pkey PRIMARY KEY (id);


--
-- Name: app_chat_retrieval_evidence app_chat_retrieval_evidence_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_chat_retrieval_evidence
    ADD CONSTRAINT app_chat_retrieval_evidence_pkey PRIMARY KEY (id);


--
-- Name: app_chat_run app_chat_run_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_chat_run
    ADD CONSTRAINT app_chat_run_pkey PRIMARY KEY (run_id);


--
-- Name: app_chat_run_replay_request app_chat_run_replay_request_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_chat_run_replay_request
    ADD CONSTRAINT app_chat_run_replay_request_pkey PRIMARY KEY (request_id);


--
-- Name: app_chat_session app_chat_session_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_chat_session
    ADD CONSTRAINT app_chat_session_pkey PRIMARY KEY (id);


--
-- Name: app_model_usage_policy_event_receipt app_model_usage_policy_event_receipt_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_model_usage_policy_event_receipt
    ADD CONSTRAINT app_model_usage_policy_event_receipt_pkey PRIMARY KEY (event_id);


--
-- Name: app_model_usage_policy_projection app_model_usage_policy_projection_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_model_usage_policy_projection
    ADD CONSTRAINT app_model_usage_policy_projection_pkey PRIMARY KEY (id);


--
-- Name: app_model_usage_policy_projection app_model_usage_policy_projection_tenant_id_model_config_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_model_usage_policy_projection
    ADD CONSTRAINT app_model_usage_policy_projection_tenant_id_model_config_id_key UNIQUE (tenant_id, model_config_id);


--
-- Name: app_publish_preflight_result app_publish_preflight_result_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_publish_preflight_result
    ADD CONSTRAINT app_publish_preflight_result_pkey PRIMARY KEY (id);


--
-- Name: appr_decision_outbox appr_decision_outbox_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appr_decision_outbox
    ADD CONSTRAINT appr_decision_outbox_pkey PRIMARY KEY (id);


--
-- Name: appr_model_approval_cancellation appr_model_approval_cancellation_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appr_model_approval_cancellation
    ADD CONSTRAINT appr_model_approval_cancellation_pkey PRIMARY KEY (id);


--
-- Name: appr_record appr_record_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appr_record
    ADD CONSTRAINT appr_record_pkey PRIMARY KEY (id);


--
-- Name: appr_task appr_task_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appr_task
    ADD CONSTRAINT appr_task_pkey PRIMARY KEY (id);


--
-- Name: audit_operation_log audit_operation_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_operation_log
    ADD CONSTRAINT audit_operation_log_pkey PRIMARY KEY (id);


--
-- Name: audit_security_log audit_security_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_security_log
    ADD CONSTRAINT audit_security_log_pkey PRIMARY KEY (id);


--
-- Name: iam_delegated_tenant_action iam_delegated_tenant_action_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_delegated_tenant_action
    ADD CONSTRAINT iam_delegated_tenant_action_pkey PRIMARY KEY (id);


--
-- Name: iam_department iam_department_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_department
    ADD CONSTRAINT iam_department_pkey PRIMARY KEY (id);


--
-- Name: iam_menu_feature iam_menu_feature_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_menu_feature
    ADD CONSTRAINT iam_menu_feature_pkey PRIMARY KEY (id);


--
-- Name: iam_menu iam_menu_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_menu
    ADD CONSTRAINT iam_menu_pkey PRIMARY KEY (id);


--
-- Name: iam_notification_outbox iam_notification_outbox_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_notification_outbox
    ADD CONSTRAINT iam_notification_outbox_pkey PRIMARY KEY (id);


--
-- Name: iam_permission iam_permission_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_permission
    ADD CONSTRAINT iam_permission_pkey PRIMARY KEY (id);


--
-- Name: iam_permission_request iam_permission_request_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_permission_request
    ADD CONSTRAINT iam_permission_request_pkey PRIMARY KEY (id);


--
-- Name: iam_resource_acl iam_resource_acl_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_resource_acl
    ADD CONSTRAINT iam_resource_acl_pkey PRIMARY KEY (id);


--
-- Name: iam_role_menu iam_role_menu_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_role_menu
    ADD CONSTRAINT iam_role_menu_pkey PRIMARY KEY (id);


--
-- Name: iam_role_permission iam_role_permission_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_role_permission
    ADD CONSTRAINT iam_role_permission_pkey PRIMARY KEY (id);


--
-- Name: iam_role iam_role_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_role
    ADD CONSTRAINT iam_role_pkey PRIMARY KEY (id);


--
-- Name: iam_tenant_feature iam_tenant_feature_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_tenant_feature
    ADD CONSTRAINT iam_tenant_feature_pkey PRIMARY KEY (id);


--
-- Name: iam_tenant iam_tenant_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_tenant
    ADD CONSTRAINT iam_tenant_pkey PRIMARY KEY (id);


--
-- Name: iam_user_invitation iam_user_invitation_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_user_invitation
    ADD CONSTRAINT iam_user_invitation_pkey PRIMARY KEY (id);


--
-- Name: iam_user iam_user_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_user
    ADD CONSTRAINT iam_user_pkey PRIMARY KEY (id);


--
-- Name: iam_user_role iam_user_role_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_user_role
    ADD CONSTRAINT iam_user_role_pkey PRIMARY KEY (id);


--
-- Name: kb_citation_record kb_citation_record_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_citation_record
    ADD CONSTRAINT kb_citation_record_pkey PRIMARY KEY (id);


--
-- Name: kb_document_chunk_embedding kb_document_chunk_embedding_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_document_chunk_embedding
    ADD CONSTRAINT kb_document_chunk_embedding_pkey PRIMARY KEY (tenant_id, chunk_id, index_version);


--
-- Name: kb_document_chunk kb_document_chunk_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_document_chunk
    ADD CONSTRAINT kb_document_chunk_pkey PRIMARY KEY (id);


--
-- Name: kb_document_object_cleanup kb_document_object_cleanup_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_document_object_cleanup
    ADD CONSTRAINT kb_document_object_cleanup_pkey PRIMARY KEY (id);


--
-- Name: kb_document kb_document_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_document
    ADD CONSTRAINT kb_document_pkey PRIMARY KEY (id);


--
-- Name: kb_embedding_job kb_embedding_job_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_embedding_job
    ADD CONSTRAINT kb_embedding_job_pkey PRIMARY KEY (id);


--
-- Name: kb_knowledge_base kb_knowledge_base_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_knowledge_base
    ADD CONSTRAINT kb_knowledge_base_pkey PRIMARY KEY (id);


--
-- Name: kb_knowledge_index_active kb_knowledge_index_active_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_knowledge_index_active
    ADD CONSTRAINT kb_knowledge_index_active_pkey PRIMARY KEY (tenant_id, knowledge_base_id);


--
-- Name: kb_knowledge_index_desired kb_knowledge_index_desired_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_knowledge_index_desired
    ADD CONSTRAINT kb_knowledge_index_desired_pkey PRIMARY KEY (tenant_id, knowledge_base_id);


--
-- Name: kb_knowledge_index_rebuild_member kb_knowledge_index_rebuild_member_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_knowledge_index_rebuild_member
    ADD CONSTRAINT kb_knowledge_index_rebuild_member_pkey PRIMARY KEY (tenant_id, rebuild_id, document_id);


--
-- Name: kb_knowledge_index_rebuild kb_knowledge_index_rebuild_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_knowledge_index_rebuild
    ADD CONSTRAINT kb_knowledge_index_rebuild_pkey PRIMARY KEY (id);


--
-- Name: kb_model_usage_policy_event_receipt kb_model_usage_policy_event_receipt_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_model_usage_policy_event_receipt
    ADD CONSTRAINT kb_model_usage_policy_event_receipt_pkey PRIMARY KEY (event_id);


--
-- Name: kb_model_usage_policy_projection kb_model_usage_policy_projection_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_model_usage_policy_projection
    ADD CONSTRAINT kb_model_usage_policy_projection_pkey PRIMARY KEY (id);


--
-- Name: mdl_approval_decision_inbox mdl_approval_decision_inbox_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mdl_approval_decision_inbox
    ADD CONSTRAINT mdl_approval_decision_inbox_pkey PRIMARY KEY (event_id);


--
-- Name: mdl_approval_request_outbox mdl_approval_request_outbox_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mdl_approval_request_outbox
    ADD CONSTRAINT mdl_approval_request_outbox_pkey PRIMARY KEY (id);


--
-- Name: mdl_config mdl_config_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mdl_config
    ADD CONSTRAINT mdl_config_pkey PRIMARY KEY (id);


--
-- Name: mdl_creation_request mdl_creation_request_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mdl_creation_request
    ADD CONSTRAINT mdl_creation_request_pkey PRIMARY KEY (id);


--
-- Name: mdl_invocation_log mdl_invocation_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mdl_invocation_log
    ADD CONSTRAINT mdl_invocation_log_pkey PRIMARY KEY (id);


--
-- Name: mdl_model_usage_policy_outbox mdl_model_usage_policy_outbox_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mdl_model_usage_policy_outbox
    ADD CONSTRAINT mdl_model_usage_policy_outbox_pkey PRIMARY KEY (id);


--
-- Name: mdl_provider mdl_provider_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mdl_provider
    ADD CONSTRAINT mdl_provider_pkey PRIMARY KEY (id);


--
-- Name: mdl_secret mdl_secret_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mdl_secret
    ADD CONSTRAINT mdl_secret_pkey PRIMARY KEY (id);


--
-- Name: rag_authorization_decision_log rag_authorization_decision_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rag_authorization_decision_log
    ADD CONSTRAINT rag_authorization_decision_log_pkey PRIMARY KEY (id);


--
-- Name: summary_metric_snapshot summary_metric_snapshot_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.summary_metric_snapshot
    ADD CONSTRAINT summary_metric_snapshot_pkey PRIMARY KEY (id);


--
-- Name: app_application_release_outbox uk_app_application_release_outbox_event; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_application_release_outbox
    ADD CONSTRAINT uk_app_application_release_outbox_event UNIQUE (event_id);


--
-- Name: app_application_release uk_app_application_release_request; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_application_release
    ADD CONSTRAINT uk_app_application_release_request UNIQUE (tenant_id, application_id, request_id);


--
-- Name: app_application_release uk_app_application_release_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_application_release
    ADD CONSTRAINT uk_app_application_release_scope UNIQUE (tenant_id, application_id, id);


--
-- Name: app_application_release uk_app_application_release_scope_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_application_release
    ADD CONSTRAINT uk_app_application_release_scope_version UNIQUE (tenant_id, application_id, id, version_no);


--
-- Name: app_application_release uk_app_application_release_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_application_release
    ADD CONSTRAINT uk_app_application_release_version UNIQUE (tenant_id, application_id, version_no);


--
-- Name: app_application uk_app_application_tenant_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_application
    ADD CONSTRAINT uk_app_application_tenant_id UNIQUE (tenant_id, id);


--
-- Name: app_chat_message_feedback uk_app_chat_message_feedback_actor; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_chat_message_feedback
    ADD CONSTRAINT uk_app_chat_message_feedback_actor UNIQUE (tenant_id, assistant_message_id, user_id);


--
-- Name: app_chat_message_feedback uk_app_chat_message_feedback_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_chat_message_feedback
    ADD CONSTRAINT uk_app_chat_message_feedback_id UNIQUE (feedback_id);


--
-- Name: app_chat_run uk_app_chat_run_client_message; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_chat_run
    ADD CONSTRAINT uk_app_chat_run_client_message UNIQUE (tenant_id, session_id, client_message_id);


--
-- Name: app_chat_run_replay_request uk_app_chat_run_replay_request_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_chat_run_replay_request
    ADD CONSTRAINT uk_app_chat_run_replay_request_id UNIQUE (tenant_id, replay_request_id);


--
-- Name: app_chat_run uk_app_chat_run_tenant_run; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_chat_run
    ADD CONSTRAINT uk_app_chat_run_tenant_run UNIQUE (tenant_id, run_id);


--
-- Name: app_chat_session uk_app_chat_session_tenant_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_chat_session
    ADD CONSTRAINT uk_app_chat_session_tenant_id UNIQUE (tenant_id, id);


--
-- Name: appr_decision_outbox uk_appr_decision_outbox_event; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appr_decision_outbox
    ADD CONSTRAINT uk_appr_decision_outbox_event UNIQUE (event_id);


--
-- Name: appr_model_approval_cancellation uk_appr_model_approval_cancellation_event; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appr_model_approval_cancellation
    ADD CONSTRAINT uk_appr_model_approval_cancellation_event UNIQUE (event_id);


--
-- Name: appr_model_approval_cancellation uk_appr_model_approval_cancellation_request; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appr_model_approval_cancellation
    ADD CONSTRAINT uk_appr_model_approval_cancellation_request UNIQUE (tenant_id, approval_request_id);


--
-- Name: iam_permission_request uk_iam_permission_request_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_permission_request
    ADD CONSTRAINT uk_iam_permission_request_id UNIQUE (request_id);


--
-- Name: iam_user uk_iam_user_tenant_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_user
    ADD CONSTRAINT uk_iam_user_tenant_id UNIQUE (tenant_id, id);


--
-- Name: kb_document_object_cleanup uk_kb_document_object_cleanup; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_document_object_cleanup
    ADD CONSTRAINT uk_kb_document_object_cleanup UNIQUE (tenant_id, bucket, object_key);


--
-- Name: kb_knowledge_index_rebuild uk_kb_index_rebuild_generation; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_knowledge_index_rebuild
    ADD CONSTRAINT uk_kb_index_rebuild_generation UNIQUE (tenant_id, knowledge_base_id, generation);


--
-- Name: kb_knowledge_index_rebuild uk_kb_index_rebuild_tenant_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_knowledge_index_rebuild
    ADD CONSTRAINT uk_kb_index_rebuild_tenant_id UNIQUE (tenant_id, id);


--
-- Name: kb_knowledge_index_rebuild uk_kb_index_rebuild_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_knowledge_index_rebuild
    ADD CONSTRAINT uk_kb_index_rebuild_version UNIQUE (tenant_id, knowledge_base_id, index_version);


--
-- Name: mdl_approval_request_outbox uk_mdl_approval_request_outbox_event; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mdl_approval_request_outbox
    ADD CONSTRAINT uk_mdl_approval_request_outbox_event UNIQUE (event_id);


--
-- Name: mdl_creation_request uk_mdl_creation_request_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mdl_creation_request
    ADD CONSTRAINT uk_mdl_creation_request_key UNIQUE (tenant_id, resource_type, request_id);


--
-- Name: mdl_model_usage_policy_outbox uk_mdl_model_usage_policy_outbox_event; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mdl_model_usage_policy_outbox
    ADD CONSTRAINT uk_mdl_model_usage_policy_outbox_event UNIQUE (event_id);


--
-- Name: idx_app_active_release_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_app_active_release_id ON public.app_application_active_release USING btree (tenant_id, active_release_id);


--
-- Name: idx_app_application_release_history; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_app_application_release_history ON public.app_application_release USING btree (tenant_id, application_id, version_no DESC);


--
-- Name: idx_app_application_release_outbox_claim; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_app_application_release_outbox_claim ON public.app_application_release_outbox USING btree (next_attempt_at, created_at, id) WHERE ((status)::text = ANY ((ARRAY['PENDING'::character varying, 'IN_FLIGHT'::character varying])::text[]));


--
-- Name: idx_app_application_release_outbox_lease; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_app_application_release_outbox_lease ON public.app_application_release_outbox USING btree (lease_until) WHERE ((status)::text = 'IN_FLIGHT'::text);


--
-- Name: idx_app_bound_kb_app; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_app_bound_kb_app ON public.app_bound_knowledge_base USING btree (tenant_id, application_id, status) WHERE (deleted_at IS NULL);


--
-- Name: idx_app_bound_kb_kb; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_app_bound_kb_kb ON public.app_bound_knowledge_base USING btree (tenant_id, knowledge_base_id) WHERE (deleted_at IS NULL);


--
-- Name: idx_app_chat_message_feedback_tenant_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_app_chat_message_feedback_tenant_time ON public.app_chat_message_feedback USING btree (tenant_id, updated_at DESC);


--
-- Name: idx_app_chat_retrieval_evidence_message; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_app_chat_retrieval_evidence_message ON public.app_chat_retrieval_evidence USING btree (tenant_id, assistant_message_id);


--
-- Name: idx_app_chat_retrieval_evidence_trace; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_app_chat_retrieval_evidence_trace ON public.app_chat_retrieval_evidence USING btree (tenant_id, trace_id);


--
-- Name: idx_app_chat_run_dead_letter; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_app_chat_run_dead_letter ON public.app_chat_run USING btree (tenant_id, status, updated_at DESC) WHERE ((status)::text = 'DEAD_LETTER'::text);


--
-- Name: idx_app_chat_run_status_lease; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_app_chat_run_status_lease ON public.app_chat_run USING btree (status, phase, lease_expires_at);


--
-- Name: idx_app_chat_run_tenant_actor_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_app_chat_run_tenant_actor_time ON public.app_chat_run USING btree (tenant_id, actor_id, created_at DESC);


--
-- Name: idx_app_chat_session_release; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_app_chat_session_release ON public.app_chat_session USING btree (tenant_id, application_id, release_id, release_version) WHERE (deleted_at IS NULL);


--
-- Name: idx_app_default_model; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_app_default_model ON public.app_application USING btree (tenant_id, default_model_config_id) WHERE (deleted_at IS NULL);


--
-- Name: idx_app_model_usage_policy_event_receipt_consumed; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_app_model_usage_policy_event_receipt_consumed ON public.app_model_usage_policy_event_receipt USING btree (consumed_at, event_id);


--
-- Name: idx_app_model_usage_policy_projection_tenant_model; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_app_model_usage_policy_projection_tenant_model ON public.app_model_usage_policy_projection USING btree (tenant_id, model_config_id);


--
-- Name: idx_app_model_usage_policy_revision; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_app_model_usage_policy_revision ON public.app_model_usage_policy_projection USING btree (tenant_id, model_config_id, policy_revision DESC);


--
-- Name: idx_app_publish_preflight_app_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_app_publish_preflight_app_time ON public.app_publish_preflight_result USING btree (tenant_id, application_id, checked_at DESC);


--
-- Name: idx_app_tenant_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_app_tenant_status ON public.app_application USING btree (tenant_id, status) WHERE (deleted_at IS NULL);


--
-- Name: idx_appr_decision_outbox_claim; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_appr_decision_outbox_claim ON public.appr_decision_outbox USING btree (next_attempt_at, created_at, id) WHERE ((status)::text = ANY ((ARRAY['PENDING'::character varying, 'IN_FLIGHT'::character varying])::text[]));


--
-- Name: idx_appr_decision_outbox_lease; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_appr_decision_outbox_lease ON public.appr_decision_outbox USING btree (lease_until) WHERE ((status)::text = 'IN_FLIGHT'::text);


--
-- Name: idx_appr_model_approval_cancellation_pending; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_appr_model_approval_cancellation_pending ON public.appr_model_approval_cancellation USING btree (tenant_id, approval_request_id, requested_at, id) WHERE ((status)::text = 'PENDING'::text);


--
-- Name: idx_appr_record_task_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_appr_record_task_time ON public.appr_record USING btree (tenant_id, task_id, action_at);


--
-- Name: idx_appr_task_approver_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_appr_task_approver_status ON public.appr_task USING btree (tenant_id, approver_id, status, due_at) WHERE (deleted_at IS NULL);


--
-- Name: idx_appr_task_source; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_appr_task_source ON public.appr_task USING btree (tenant_id, source_context, source_resource_type, source_resource_id) WHERE (deleted_at IS NULL);


--
-- Name: idx_appr_task_tenant_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_appr_task_tenant_time ON public.appr_task USING btree (tenant_id, created_at DESC) WHERE (deleted_at IS NULL);


--
-- Name: idx_audit_operation_actor_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_operation_actor_time ON public.audit_operation_log USING btree (tenant_id, actor_id, created_at DESC);


--
-- Name: idx_audit_operation_resource; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_operation_resource ON public.audit_operation_log USING btree (tenant_id, resource_type, resource_id, created_at DESC);


--
-- Name: idx_audit_operation_resource_ref; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_operation_resource_ref ON public.audit_operation_log USING btree (tenant_id, resource_type, resource_ref, created_at DESC);


--
-- Name: idx_audit_operation_tenant_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_operation_tenant_time ON public.audit_operation_log USING btree (tenant_id, created_at DESC);


--
-- Name: idx_audit_operation_trace; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_operation_trace ON public.audit_operation_log USING btree (trace_id);


--
-- Name: idx_audit_security_actor_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_security_actor_time ON public.audit_security_log USING btree (tenant_id, actor_id, created_at DESC);


--
-- Name: idx_audit_security_subject; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_security_subject ON public.audit_security_log USING btree (tenant_id, subject_type, subject_id, created_at DESC);


--
-- Name: idx_audit_security_subject_ref; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_security_subject_ref ON public.audit_security_log USING btree (tenant_id, subject_type, subject_ref, created_at DESC);


--
-- Name: idx_audit_security_tenant_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_security_tenant_time ON public.audit_security_log USING btree (tenant_id, created_at DESC);


--
-- Name: idx_audit_security_trace; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_security_trace ON public.audit_security_log USING btree (trace_id);


--
-- Name: idx_chat_message_invocation; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chat_message_invocation ON public.app_chat_message USING btree (tenant_id, model_invocation_log_id) WHERE (model_invocation_log_id IS NOT NULL);


--
-- Name: idx_chat_message_session_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chat_message_session_time ON public.app_chat_message USING btree (tenant_id, session_id, created_at);


--
-- Name: idx_chat_message_trace; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chat_message_trace ON public.app_chat_message USING btree (trace_id);


--
-- Name: idx_chat_session_app_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chat_session_app_time ON public.app_chat_session USING btree (tenant_id, application_id, last_message_at DESC) WHERE (deleted_at IS NULL);


--
-- Name: idx_chat_session_user_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chat_session_user_time ON public.app_chat_session USING btree (tenant_id, user_id, last_message_at DESC) WHERE (deleted_at IS NULL);


--
-- Name: idx_iam_acl_resource; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_iam_acl_resource ON public.iam_resource_acl USING btree (tenant_id, resource_type, resource_id) WHERE (deleted_at IS NULL);


--
-- Name: idx_iam_acl_subject; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_iam_acl_subject ON public.iam_resource_acl USING btree (tenant_id, subject_type, subject_id, permission_code) WHERE (deleted_at IS NULL);


--
-- Name: idx_iam_delegated_tenant_action_tenant_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_iam_delegated_tenant_action_tenant_time ON public.iam_delegated_tenant_action USING btree (tenant_id, created_at DESC);


--
-- Name: idx_iam_dept_tenant_parent; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_iam_dept_tenant_parent ON public.iam_department USING btree (tenant_id, parent_id, sort_order) WHERE (deleted_at IS NULL);


--
-- Name: idx_iam_dept_tenant_path; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_iam_dept_tenant_path ON public.iam_department USING btree (tenant_id, path text_pattern_ops) WHERE (deleted_at IS NULL);


--
-- Name: idx_iam_menu_feature_code; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_iam_menu_feature_code ON public.iam_menu_feature USING btree (feature_code);


--
-- Name: idx_iam_menu_permission; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_iam_menu_permission ON public.iam_menu USING btree (required_permission_code) WHERE (deleted_at IS NULL);


--
-- Name: idx_iam_menu_scope_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_iam_menu_scope_status ON public.iam_menu USING btree (scope, status, sort_order) WHERE (deleted_at IS NULL);


--
-- Name: idx_iam_notification_outbox_delivery; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_iam_notification_outbox_delivery ON public.iam_notification_outbox USING btree (delivery_status, created_at);


--
-- Name: idx_iam_permission_request_tenant_priority_created_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_iam_permission_request_tenant_priority_created_id ON public.iam_permission_request USING btree (tenant_id, (
CASE status
    WHEN 'PENDING'::text THEN 0
    ELSE 1
END), created_at DESC, request_id DESC);


--
-- Name: INDEX idx_iam_permission_request_tenant_priority_created_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON INDEX public.idx_iam_permission_request_tenant_priority_created_id IS '权限申请租户分页的稳定优先级、创建时间与 ID 游标顺序';


--
-- Name: idx_iam_permission_request_tenant_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_iam_permission_request_tenant_status ON public.iam_permission_request USING btree (tenant_id, status, created_at DESC);


--
-- Name: idx_iam_permission_resource_action; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_iam_permission_resource_action ON public.iam_permission USING btree (resource_type, action_code);


--
-- Name: idx_iam_role_menu_menu; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_iam_role_menu_menu ON public.iam_role_menu USING btree (menu_id) WHERE (deleted_at IS NULL);


--
-- Name: idx_iam_role_menu_role; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_iam_role_menu_role ON public.iam_role_menu USING btree (tenant_id, role_id) WHERE (deleted_at IS NULL);


--
-- Name: idx_iam_role_permission_role; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_iam_role_permission_role ON public.iam_role_permission USING btree (tenant_id, role_id) WHERE (deleted_at IS NULL);


--
-- Name: idx_iam_tenant_feature_enabled; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_iam_tenant_feature_enabled ON public.iam_tenant_feature USING btree (tenant_id, enabled, feature_code);


--
-- Name: idx_iam_tenant_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_iam_tenant_status ON public.iam_tenant USING btree (status) WHERE (deleted_at IS NULL);


--
-- Name: idx_iam_user_invitation_digest; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_iam_user_invitation_digest ON public.iam_user_invitation USING btree (token_digest) WHERE ((status)::text = 'PENDING'::text);


--
-- Name: idx_iam_user_role_role; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_iam_user_role_role ON public.iam_user_role USING btree (tenant_id, role_id) WHERE (deleted_at IS NULL);


--
-- Name: idx_iam_user_role_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_iam_user_role_user ON public.iam_user_role USING btree (tenant_id, user_id) WHERE (deleted_at IS NULL);


--
-- Name: idx_iam_user_tenant_dept; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_iam_user_tenant_dept ON public.iam_user USING btree (tenant_id, department_id) WHERE (deleted_at IS NULL);


--
-- Name: idx_iam_user_tenant_display_name_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_iam_user_tenant_display_name_id ON public.iam_user USING btree (tenant_id, display_name, id) WHERE (deleted_at IS NULL);


--
-- Name: INDEX idx_iam_user_tenant_display_name_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON INDEX public.idx_iam_user_tenant_display_name_id IS '租户用户目录按显示名和用户 ID 稳定分页的覆盖索引';


--
-- Name: idx_iam_user_tenant_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_iam_user_tenant_status ON public.iam_user USING btree (tenant_id, status) WHERE (deleted_at IS NULL);


--
-- Name: idx_kb_chunk_acl_scope; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_kb_chunk_acl_scope ON public.kb_document_chunk USING btree (tenant_id, knowledge_base_id, classification, visibility, status) WHERE (deleted_at IS NULL);


--
-- Name: idx_kb_chunk_active_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_kb_chunk_active_index ON public.kb_document_chunk USING btree (tenant_id, knowledge_base_id, index_version, status) WHERE (deleted_at IS NULL);


--
-- Name: idx_kb_chunk_document; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_kb_chunk_document ON public.kb_document_chunk USING btree (tenant_id, document_id) WHERE (deleted_at IS NULL);


--
-- Name: idx_kb_chunk_embedding_candidate; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_kb_chunk_embedding_candidate ON public.kb_document_chunk_embedding USING btree (tenant_id, knowledge_base_id, embedding_model, index_version, chunk_id);


--
-- Name: idx_kb_chunk_embedding_cleanup; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_kb_chunk_embedding_cleanup ON public.kb_document_chunk_embedding USING btree (updated_at, tenant_id, knowledge_base_id);


--
-- Name: idx_kb_chunk_kb_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_kb_chunk_kb_status ON public.kb_document_chunk USING btree (tenant_id, knowledge_base_id, status) WHERE (deleted_at IS NULL);


--
-- Name: idx_kb_chunk_keyword_tsv; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_kb_chunk_keyword_tsv ON public.kb_document_chunk USING gin (to_tsvector('simple'::regconfig, content)) WHERE ((deleted_at IS NULL) AND ((status)::text = 'ACTIVE'::text));


--
-- Name: idx_kb_citation_chunk; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_kb_citation_chunk ON public.kb_citation_record USING btree (tenant_id, chunk_id, created_at DESC);


--
-- Name: idx_kb_citation_message; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_kb_citation_message ON public.kb_citation_record USING btree (tenant_id, chat_message_id);


--
-- Name: idx_kb_document_kb_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_kb_document_kb_status ON public.kb_document USING btree (tenant_id, knowledge_base_id, parse_status) WHERE (deleted_at IS NULL);


--
-- Name: idx_kb_document_tenant_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_kb_document_tenant_time ON public.kb_document USING btree (tenant_id, created_at DESC) WHERE (deleted_at IS NULL);


--
-- Name: idx_kb_embedding_model; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_kb_embedding_model ON public.kb_knowledge_base USING btree (tenant_id, embedding_model_config_id) WHERE (deleted_at IS NULL);


--
-- Name: idx_kb_index_member_document; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_kb_index_member_document ON public.kb_knowledge_index_rebuild_member USING btree (tenant_id, knowledge_base_id, document_id);


--
-- Name: idx_kb_job_document; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_kb_job_document ON public.kb_embedding_job USING btree (tenant_id, document_id, created_at DESC) WHERE (deleted_at IS NULL);


--
-- Name: idx_kb_job_document_latest; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_kb_job_document_latest ON public.kb_embedding_job USING btree (tenant_id, knowledge_base_id, document_id, created_at DESC, id DESC) WHERE (deleted_at IS NULL);


--
-- Name: idx_kb_job_expired_lease; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_kb_job_expired_lease ON public.kb_embedding_job USING btree (lease_until, id) WHERE ((deleted_at IS NULL) AND ((status)::text = ANY ((ARRAY['PARSING'::character varying, 'INDEXING'::character varying])::text[])));


--
-- Name: idx_kb_job_recovery_due; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_kb_job_recovery_due ON public.kb_embedding_job USING btree (next_retry_at, created_at, id) WHERE ((deleted_at IS NULL) AND ((status)::text = ANY ((ARRAY['PENDING'::character varying, 'RETRY_WAIT'::character varying])::text[])));


--
-- Name: idx_kb_job_status_retry; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_kb_job_status_retry ON public.kb_embedding_job USING btree (tenant_id, status, next_retry_at) WHERE (deleted_at IS NULL);


--
-- Name: idx_kb_model_policy_enabled; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_kb_model_policy_enabled ON public.kb_model_usage_policy_projection USING btree (tenant_id, enabled, last_event_at DESC) WHERE (deleted_at IS NULL);


--
-- Name: idx_kb_model_usage_policy_event_receipt_consumed; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_kb_model_usage_policy_event_receipt_consumed ON public.kb_model_usage_policy_event_receipt USING btree (consumed_at, event_id);


--
-- Name: idx_kb_model_usage_policy_revision; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_kb_model_usage_policy_revision ON public.kb_model_usage_policy_projection USING btree (tenant_id, model_config_id, policy_revision DESC) WHERE (deleted_at IS NULL);


--
-- Name: idx_kb_object_cleanup_due; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_kb_object_cleanup_due ON public.kb_document_object_cleanup USING btree (next_attempt_at, observed_at, id) WHERE ((status)::text = ANY ((ARRAY['PENDING'::character varying, 'RETRY_WAIT'::character varying])::text[]));


--
-- Name: idx_kb_object_cleanup_expired_lease; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_kb_object_cleanup_expired_lease ON public.kb_document_object_cleanup USING btree (lease_until, id) WHERE ((status)::text = 'PROCESSING'::text);


--
-- Name: idx_kb_tenant_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_kb_tenant_status ON public.kb_knowledge_base USING btree (tenant_id, status) WHERE (deleted_at IS NULL);


--
-- Name: idx_mdl_approval_decision_inbox_tenant_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_mdl_approval_decision_inbox_tenant_time ON public.mdl_approval_decision_inbox USING btree (tenant_id, processed_at DESC, event_id);


--
-- Name: idx_mdl_approval_request_outbox_claim; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_mdl_approval_request_outbox_claim ON public.mdl_approval_request_outbox USING btree (next_attempt_at, created_at, id) WHERE ((status)::text = ANY ((ARRAY['PENDING'::character varying, 'IN_FLIGHT'::character varying])::text[]));


--
-- Name: idx_mdl_approval_request_outbox_lease; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_mdl_approval_request_outbox_lease ON public.mdl_approval_request_outbox USING btree (lease_until) WHERE ((status)::text = 'IN_FLIGHT'::text);


--
-- Name: idx_mdl_config_tenant_type_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_mdl_config_tenant_type_status ON public.mdl_config USING btree (tenant_id, model_type, status) WHERE (deleted_at IS NULL);


--
-- Name: idx_mdl_invocation_idempotency; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_mdl_invocation_idempotency ON public.mdl_invocation_log USING btree (tenant_id, idempotency_key, created_at DESC);


--
-- Name: idx_mdl_invocation_model_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_mdl_invocation_model_time ON public.mdl_invocation_log USING btree (tenant_id, model_config_id, created_at DESC);


--
-- Name: idx_mdl_invocation_source; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_mdl_invocation_source ON public.mdl_invocation_log USING btree (tenant_id, source_context, source_resource_type, source_resource_id, created_at DESC);


--
-- Name: idx_mdl_invocation_tenant_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_mdl_invocation_tenant_time ON public.mdl_invocation_log USING btree (tenant_id, created_at DESC);


--
-- Name: idx_mdl_invocation_trace; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_mdl_invocation_trace ON public.mdl_invocation_log USING btree (trace_id);


--
-- Name: idx_mdl_model_usage_policy_outbox_claim; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_mdl_model_usage_policy_outbox_claim ON public.mdl_model_usage_policy_outbox USING btree (next_attempt_at, created_at, id) WHERE ((status)::text = ANY ((ARRAY['PENDING'::character varying, 'IN_FLIGHT'::character varying])::text[]));


--
-- Name: idx_mdl_model_usage_policy_outbox_lease; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_mdl_model_usage_policy_outbox_lease ON public.mdl_model_usage_policy_outbox USING btree (lease_until) WHERE ((status)::text = 'IN_FLIGHT'::text);


--
-- Name: idx_mdl_provider_tenant_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_mdl_provider_tenant_status ON public.mdl_provider USING btree (tenant_id, status) WHERE (deleted_at IS NULL);


--
-- Name: idx_mdl_secret_digest; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_mdl_secret_digest ON public.mdl_secret USING btree (tenant_id, secret_digest) WHERE (deleted_at IS NULL);


--
-- Name: idx_mdl_secret_provider_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_mdl_secret_provider_status ON public.mdl_secret USING btree (tenant_id, provider_id, status) WHERE (deleted_at IS NULL);


--
-- Name: idx_mdl_secret_rotation_backlog; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_mdl_secret_rotation_backlog ON public.mdl_secret USING btree (kms_key_id, cipher_algorithm, cipher_version, id) WHERE (((status)::text = 'ACTIVE'::text) AND (deleted_at IS NULL));


--
-- Name: idx_rag_auth_decision_tenant_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_rag_auth_decision_tenant_time ON public.rag_authorization_decision_log USING btree (tenant_id, created_at DESC);


--
-- Name: idx_rag_auth_decision_trace; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_rag_auth_decision_trace ON public.rag_authorization_decision_log USING btree (tenant_id, trace_id);


--
-- Name: idx_summary_metric_snapshot_scope_asof; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_summary_metric_snapshot_scope_asof ON public.summary_metric_snapshot USING btree (tenant_id, scope_type, as_of DESC);


--
-- Name: uk_app_bound_kb; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_app_bound_kb ON public.app_bound_knowledge_base USING btree (tenant_id, application_id, knowledge_base_id) WHERE (deleted_at IS NULL);


--
-- Name: uk_app_chat_message_client_request; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_app_chat_message_client_request ON public.app_chat_message USING btree (tenant_id, session_id, client_message_id) WHERE (((role)::text = 'user'::text) AND (client_message_id IS NOT NULL));


--
-- Name: uk_app_chat_run_replay_request; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_app_chat_run_replay_request ON public.app_chat_run USING btree (tenant_id, last_replay_request_id) WHERE (last_replay_request_id IS NOT NULL);


--
-- Name: uk_app_publish_preflight_request; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_app_publish_preflight_request ON public.app_publish_preflight_result USING btree (tenant_id, application_id, publish_request_id);


--
-- Name: uk_app_tenant_code; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_app_tenant_code ON public.app_application USING btree (tenant_id, app_code) WHERE (deleted_at IS NULL);


--
-- Name: uk_appr_record_cancel_once; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_appr_record_cancel_once ON public.appr_record USING btree (tenant_id, task_id, action) WHERE ((action)::text = 'CANCEL'::text);


--
-- Name: uk_appr_task_pending_business_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_appr_task_pending_business_key ON public.appr_task USING btree (tenant_id, approval_type, source_context, source_resource_type, source_resource_id) WHERE (((status)::text = 'PENDING'::text) AND (deleted_at IS NULL));


--
-- Name: uk_appr_task_tenant_id_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_appr_task_tenant_id_id ON public.appr_task USING btree (tenant_id, id);


--
-- Name: uk_appr_task_tenant_request_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_appr_task_tenant_request_id ON public.appr_task USING btree (tenant_id, approval_request_id);


--
-- Name: uk_appr_task_tenant_task_request; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_appr_task_tenant_task_request ON public.appr_task USING btree (tenant_id, id, approval_request_id);


--
-- Name: uk_iam_acl_resource_subject_permission; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_iam_acl_resource_subject_permission ON public.iam_resource_acl USING btree (tenant_id, resource_type, resource_id, subject_type, subject_id, permission_code) WHERE (deleted_at IS NULL);


--
-- Name: uk_iam_delegated_tenant_action_request; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_iam_delegated_tenant_action_request ON public.iam_delegated_tenant_action USING btree (tenant_id, operator_id, request_hash);


--
-- Name: uk_iam_dept_tenant_code; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_iam_dept_tenant_code ON public.iam_department USING btree (tenant_id, dept_code) WHERE (deleted_at IS NULL);


--
-- Name: uk_iam_menu_code; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_iam_menu_code ON public.iam_menu USING btree (menu_code) WHERE (deleted_at IS NULL);


--
-- Name: uk_iam_menu_feature; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_iam_menu_feature ON public.iam_menu_feature USING btree (menu_id, feature_code);


--
-- Name: uk_iam_menu_path; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_iam_menu_path ON public.iam_menu USING btree (path) WHERE (deleted_at IS NULL);


--
-- Name: uk_iam_permission_code; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_iam_permission_code ON public.iam_permission USING btree (permission_code);


--
-- Name: uk_iam_role_menu; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_iam_role_menu ON public.iam_role_menu USING btree (tenant_id, role_id, menu_id) WHERE (deleted_at IS NULL);


--
-- Name: uk_iam_role_permission; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_iam_role_permission ON public.iam_role_permission USING btree (tenant_id, role_id, permission_id) WHERE (deleted_at IS NULL);


--
-- Name: uk_iam_role_tenant_code; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_iam_role_tenant_code ON public.iam_role USING btree (tenant_id, role_code) WHERE (deleted_at IS NULL);


--
-- Name: uk_iam_tenant_code; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_iam_tenant_code ON public.iam_tenant USING btree (tenant_code) WHERE (deleted_at IS NULL);


--
-- Name: uk_iam_tenant_feature; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_iam_tenant_feature ON public.iam_tenant_feature USING btree (tenant_id, feature_code);


--
-- Name: uk_iam_user_invitation_pending_user; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_iam_user_invitation_pending_user ON public.iam_user_invitation USING btree (tenant_id, user_id) WHERE ((status)::text = 'PENDING'::text);


--
-- Name: uk_iam_user_role; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_iam_user_role ON public.iam_user_role USING btree (tenant_id, user_id, role_id) WHERE (deleted_at IS NULL);


--
-- Name: uk_iam_user_tenant_email; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_iam_user_tenant_email ON public.iam_user USING btree (tenant_id, email) WHERE ((email IS NOT NULL) AND (deleted_at IS NULL));


--
-- Name: uk_iam_user_tenant_username; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_iam_user_tenant_username ON public.iam_user USING btree (tenant_id, username) WHERE (deleted_at IS NULL);


--
-- Name: uk_kb_chunk_document_no; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_kb_chunk_document_no ON public.kb_document_chunk USING btree (tenant_id, document_id, chunk_no) WHERE (deleted_at IS NULL);


--
-- Name: uk_kb_document_content_hash; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_kb_document_content_hash ON public.kb_document USING btree (tenant_id, knowledge_base_id, content_hash) WHERE (deleted_at IS NULL);


--
-- Name: uk_kb_document_tenant_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_kb_document_tenant_id ON public.kb_document USING btree (tenant_id, id);


--
-- Name: uk_kb_index_job_member; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_kb_index_job_member ON public.kb_embedding_job USING btree (tenant_id, rebuild_id, document_id) WHERE (((job_type)::text = 'DOCUMENT_INDEX'::text) AND (deleted_at IS NULL));


--
-- Name: uk_kb_index_rebuild_active; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_kb_index_rebuild_active ON public.kb_knowledge_index_rebuild USING btree (tenant_id, knowledge_base_id) WHERE ((status)::text = 'ACTIVE'::text);


--
-- Name: uk_kb_knowledge_base_tenant_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_kb_knowledge_base_tenant_id ON public.kb_knowledge_base USING btree (tenant_id, id);


--
-- Name: uk_kb_model_policy_projection; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_kb_model_policy_projection ON public.kb_model_usage_policy_projection USING btree (tenant_id, model_config_id) WHERE (deleted_at IS NULL);


--
-- Name: uk_kb_tenant_code; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_kb_tenant_code ON public.kb_knowledge_base USING btree (tenant_id, kb_code) WHERE (deleted_at IS NULL);


--
-- Name: uk_mdl_config_tenant_approval_request; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_mdl_config_tenant_approval_request ON public.mdl_config USING btree (tenant_id, approval_request_id) WHERE (approval_request_id IS NOT NULL);


--
-- Name: uk_mdl_config_tenant_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_mdl_config_tenant_id ON public.mdl_config USING btree (tenant_id, id);


--
-- Name: uk_mdl_config_tenant_provider_model; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_mdl_config_tenant_provider_model ON public.mdl_config USING btree (tenant_id, provider_id, model_code) WHERE (deleted_at IS NULL);


--
-- Name: uk_mdl_provider_tenant_approval_request; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_mdl_provider_tenant_approval_request ON public.mdl_provider USING btree (tenant_id, approval_request_id) WHERE (approval_request_id IS NOT NULL);


--
-- Name: uk_mdl_provider_tenant_code; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_mdl_provider_tenant_code ON public.mdl_provider USING btree (tenant_id, provider_code) WHERE (deleted_at IS NULL);


--
-- Name: uk_summary_metric_snapshot_scope; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_summary_metric_snapshot_scope ON public.summary_metric_snapshot USING btree (tenant_id, scope_type, metric_code);


--
-- Name: app_application_release trg_app_application_release_immutable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_app_application_release_immutable BEFORE DELETE OR UPDATE ON public.app_application_release FOR EACH ROW EXECUTE FUNCTION public.reject_app_application_release_mutation();


--
-- Name: app_chat_run trg_app_chat_run_finalization_counter_guard; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_app_chat_run_finalization_counter_guard BEFORE UPDATE ON public.app_chat_run FOR EACH ROW EXECUTE FUNCTION public.app_chat_run_finalization_counter_guard();


--
-- Name: app_application app_application_default_model_config_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_application
    ADD CONSTRAINT app_application_default_model_config_id_fkey FOREIGN KEY (default_model_config_id) REFERENCES public.mdl_config(id);


--
-- Name: app_application_release_outbox app_application_release_outbox_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_application_release_outbox
    ADD CONSTRAINT app_application_release_outbox_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.iam_tenant(id);


--
-- Name: app_application_release app_application_release_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_application_release
    ADD CONSTRAINT app_application_release_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.iam_tenant(id);


--
-- Name: app_application app_application_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_application
    ADD CONSTRAINT app_application_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.iam_tenant(id);


--
-- Name: app_bound_knowledge_base app_bound_knowledge_base_application_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_bound_knowledge_base
    ADD CONSTRAINT app_bound_knowledge_base_application_id_fkey FOREIGN KEY (application_id) REFERENCES public.app_application(id);


--
-- Name: app_bound_knowledge_base app_bound_knowledge_base_knowledge_base_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_bound_knowledge_base
    ADD CONSTRAINT app_bound_knowledge_base_knowledge_base_id_fkey FOREIGN KEY (knowledge_base_id) REFERENCES public.kb_knowledge_base(id);


--
-- Name: app_bound_knowledge_base app_bound_knowledge_base_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_bound_knowledge_base
    ADD CONSTRAINT app_bound_knowledge_base_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.iam_tenant(id);


--
-- Name: app_chat_message app_chat_message_application_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_chat_message
    ADD CONSTRAINT app_chat_message_application_id_fkey FOREIGN KEY (application_id) REFERENCES public.app_application(id);


--
-- Name: app_chat_message_feedback app_chat_message_feedback_assistant_message_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_chat_message_feedback
    ADD CONSTRAINT app_chat_message_feedback_assistant_message_id_fkey FOREIGN KEY (assistant_message_id) REFERENCES public.app_chat_message(id);


--
-- Name: app_chat_message_feedback app_chat_message_feedback_session_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_chat_message_feedback
    ADD CONSTRAINT app_chat_message_feedback_session_id_fkey FOREIGN KEY (session_id) REFERENCES public.app_chat_session(id);


--
-- Name: app_chat_message app_chat_message_model_invocation_log_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_chat_message
    ADD CONSTRAINT app_chat_message_model_invocation_log_id_fkey FOREIGN KEY (model_invocation_log_id) REFERENCES public.mdl_invocation_log(id);


--
-- Name: app_chat_message app_chat_message_session_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_chat_message
    ADD CONSTRAINT app_chat_message_session_id_fkey FOREIGN KEY (session_id) REFERENCES public.app_chat_session(id);


--
-- Name: app_chat_message app_chat_message_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_chat_message
    ADD CONSTRAINT app_chat_message_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.iam_tenant(id);


--
-- Name: app_chat_retrieval_evidence app_chat_retrieval_evidence_assistant_message_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_chat_retrieval_evidence
    ADD CONSTRAINT app_chat_retrieval_evidence_assistant_message_id_fkey FOREIGN KEY (assistant_message_id) REFERENCES public.app_chat_message(id);


--
-- Name: app_chat_run app_chat_run_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_chat_run
    ADD CONSTRAINT app_chat_run_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.iam_tenant(id);


--
-- Name: app_chat_session app_chat_session_application_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_chat_session
    ADD CONSTRAINT app_chat_session_application_id_fkey FOREIGN KEY (application_id) REFERENCES public.app_application(id);


--
-- Name: app_chat_session app_chat_session_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_chat_session
    ADD CONSTRAINT app_chat_session_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.iam_tenant(id);


--
-- Name: app_chat_session app_chat_session_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_chat_session
    ADD CONSTRAINT app_chat_session_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.iam_user(id);


--
-- Name: appr_decision_outbox appr_decision_outbox_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appr_decision_outbox
    ADD CONSTRAINT appr_decision_outbox_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.iam_tenant(id);


--
-- Name: appr_model_approval_cancellation appr_model_approval_cancellation_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appr_model_approval_cancellation
    ADD CONSTRAINT appr_model_approval_cancellation_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.iam_tenant(id);


--
-- Name: appr_record appr_record_actor_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appr_record
    ADD CONSTRAINT appr_record_actor_id_fkey FOREIGN KEY (actor_id) REFERENCES public.iam_user(id);


--
-- Name: appr_record appr_record_task_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appr_record
    ADD CONSTRAINT appr_record_task_id_fkey FOREIGN KEY (task_id) REFERENCES public.appr_task(id);


--
-- Name: appr_record appr_record_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appr_record
    ADD CONSTRAINT appr_record_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.iam_tenant(id);


--
-- Name: appr_task appr_task_approver_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appr_task
    ADD CONSTRAINT appr_task_approver_id_fkey FOREIGN KEY (approver_id) REFERENCES public.iam_user(id);


--
-- Name: appr_task appr_task_requester_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appr_task
    ADD CONSTRAINT appr_task_requester_id_fkey FOREIGN KEY (requester_id) REFERENCES public.iam_user(id);


--
-- Name: appr_task appr_task_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appr_task
    ADD CONSTRAINT appr_task_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.iam_tenant(id);


--
-- Name: audit_operation_log audit_operation_log_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_operation_log
    ADD CONSTRAINT audit_operation_log_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.iam_tenant(id);


--
-- Name: audit_security_log audit_security_log_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_security_log
    ADD CONSTRAINT audit_security_log_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.iam_tenant(id);


--
-- Name: app_application_active_release fk_app_active_release_application; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_application_active_release
    ADD CONSTRAINT fk_app_active_release_application FOREIGN KEY (tenant_id, application_id) REFERENCES public.app_application(tenant_id, id);


--
-- Name: app_application_active_release fk_app_active_release_snapshot; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_application_active_release
    ADD CONSTRAINT fk_app_active_release_snapshot FOREIGN KEY (tenant_id, application_id, active_release_id) REFERENCES public.app_application_release(tenant_id, application_id, id);


--
-- Name: app_application_release fk_app_application_release_application; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_application_release
    ADD CONSTRAINT fk_app_application_release_application FOREIGN KEY (tenant_id, application_id) REFERENCES public.app_application(tenant_id, id);


--
-- Name: app_application_release fk_app_application_release_source; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_application_release
    ADD CONSTRAINT fk_app_application_release_source FOREIGN KEY (tenant_id, application_id, source_release_id) REFERENCES public.app_application_release(tenant_id, application_id, id);


--
-- Name: app_chat_run fk_app_chat_run_actor_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_chat_run
    ADD CONSTRAINT fk_app_chat_run_actor_tenant FOREIGN KEY (tenant_id, actor_id) REFERENCES public.iam_user(tenant_id, id);


--
-- Name: app_chat_run fk_app_chat_run_application_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_chat_run
    ADD CONSTRAINT fk_app_chat_run_application_tenant FOREIGN KEY (tenant_id, application_id) REFERENCES public.app_application(tenant_id, id);


--
-- Name: app_chat_run_replay_request fk_app_chat_run_replay_actor_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_chat_run_replay_request
    ADD CONSTRAINT fk_app_chat_run_replay_actor_tenant FOREIGN KEY (tenant_id, actor_id) REFERENCES public.iam_user(tenant_id, id);


--
-- Name: app_chat_run_replay_request fk_app_chat_run_replay_run_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_chat_run_replay_request
    ADD CONSTRAINT fk_app_chat_run_replay_run_tenant FOREIGN KEY (tenant_id, run_id) REFERENCES public.app_chat_run(tenant_id, run_id);


--
-- Name: app_chat_run fk_app_chat_run_session_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_chat_run
    ADD CONSTRAINT fk_app_chat_run_session_tenant FOREIGN KEY (tenant_id, session_id) REFERENCES public.app_chat_session(tenant_id, id);


--
-- Name: app_chat_session fk_app_chat_session_release; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_chat_session
    ADD CONSTRAINT fk_app_chat_session_release FOREIGN KEY (tenant_id, application_id, release_id, release_version) REFERENCES public.app_application_release(tenant_id, application_id, id, version_no);


--
-- Name: appr_model_approval_cancellation fk_appr_model_approval_cancellation_actor; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appr_model_approval_cancellation
    ADD CONSTRAINT fk_appr_model_approval_cancellation_actor FOREIGN KEY (tenant_id, actor_id) REFERENCES public.iam_user(tenant_id, id);


--
-- Name: appr_model_approval_cancellation fk_appr_model_approval_cancellation_task; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.appr_model_approval_cancellation
    ADD CONSTRAINT fk_appr_model_approval_cancellation_task FOREIGN KEY (tenant_id, task_id) REFERENCES public.appr_task(tenant_id, id);


--
-- Name: kb_knowledge_index_desired fk_kb_index_desired_kb_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_knowledge_index_desired
    ADD CONSTRAINT fk_kb_index_desired_kb_tenant FOREIGN KEY (tenant_id, knowledge_base_id) REFERENCES public.kb_knowledge_base(tenant_id, id);


--
-- Name: kb_knowledge_index_desired fk_kb_index_desired_rebuild; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_knowledge_index_desired
    ADD CONSTRAINT fk_kb_index_desired_rebuild FOREIGN KEY (tenant_id, desired_rebuild_id) REFERENCES public.kb_knowledge_index_rebuild(tenant_id, id);


--
-- Name: kb_knowledge_index_rebuild_member fk_kb_index_member_document_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_knowledge_index_rebuild_member
    ADD CONSTRAINT fk_kb_index_member_document_tenant FOREIGN KEY (tenant_id, document_id) REFERENCES public.kb_document(tenant_id, id);


--
-- Name: kb_knowledge_index_rebuild_member fk_kb_index_member_kb_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_knowledge_index_rebuild_member
    ADD CONSTRAINT fk_kb_index_member_kb_tenant FOREIGN KEY (tenant_id, knowledge_base_id) REFERENCES public.kb_knowledge_base(tenant_id, id);


--
-- Name: kb_knowledge_index_rebuild_member fk_kb_index_member_rebuild; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_knowledge_index_rebuild_member
    ADD CONSTRAINT fk_kb_index_member_rebuild FOREIGN KEY (tenant_id, rebuild_id) REFERENCES public.kb_knowledge_index_rebuild(tenant_id, id) ON DELETE CASCADE;


--
-- Name: kb_knowledge_index_rebuild fk_kb_index_rebuild_kb_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_knowledge_index_rebuild
    ADD CONSTRAINT fk_kb_index_rebuild_kb_tenant FOREIGN KEY (tenant_id, knowledge_base_id) REFERENCES public.kb_knowledge_base(tenant_id, id);


--
-- Name: kb_knowledge_index_rebuild fk_kb_index_rebuild_model_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_knowledge_index_rebuild
    ADD CONSTRAINT fk_kb_index_rebuild_model_tenant FOREIGN KEY (tenant_id, target_model_config_id) REFERENCES public.mdl_config(tenant_id, id);


--
-- Name: kb_embedding_job fk_kb_job_cancel_actor_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_embedding_job
    ADD CONSTRAINT fk_kb_job_cancel_actor_tenant FOREIGN KEY (tenant_id, cancelled_by) REFERENCES public.iam_user(tenant_id, id);


--
-- Name: kb_embedding_job fk_kb_job_rebuild; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_embedding_job
    ADD CONSTRAINT fk_kb_job_rebuild FOREIGN KEY (tenant_id, rebuild_id) REFERENCES public.kb_knowledge_index_rebuild(tenant_id, id);


--
-- Name: kb_embedding_job fk_kb_job_target_model; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_embedding_job
    ADD CONSTRAINT fk_kb_job_target_model FOREIGN KEY (tenant_id, target_model_config_id) REFERENCES public.mdl_config(tenant_id, id);


--
-- Name: kb_document_object_cleanup fk_kb_object_cleanup_kb_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_document_object_cleanup
    ADD CONSTRAINT fk_kb_object_cleanup_kb_tenant FOREIGN KEY (tenant_id, knowledge_base_id) REFERENCES public.kb_knowledge_base(tenant_id, id);


--
-- Name: mdl_approval_decision_inbox fk_mdl_approval_decision_inbox_task_request; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mdl_approval_decision_inbox
    ADD CONSTRAINT fk_mdl_approval_decision_inbox_task_request FOREIGN KEY (tenant_id, task_id, approval_request_id) REFERENCES public.appr_task(tenant_id, id, approval_request_id);


--
-- Name: mdl_approval_decision_inbox fk_mdl_approval_decision_inbox_task_tenant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mdl_approval_decision_inbox
    ADD CONSTRAINT fk_mdl_approval_decision_inbox_task_tenant FOREIGN KEY (tenant_id, task_id) REFERENCES public.appr_task(tenant_id, id);


--
-- Name: iam_department iam_department_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_department
    ADD CONSTRAINT iam_department_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.iam_tenant(id);


--
-- Name: iam_menu_feature iam_menu_feature_menu_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_menu_feature
    ADD CONSTRAINT iam_menu_feature_menu_id_fkey FOREIGN KEY (menu_id) REFERENCES public.iam_menu(id);


--
-- Name: iam_menu iam_menu_parent_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_menu
    ADD CONSTRAINT iam_menu_parent_id_fkey FOREIGN KEY (parent_id) REFERENCES public.iam_menu(id);


--
-- Name: iam_menu iam_menu_required_permission_code_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_menu
    ADD CONSTRAINT iam_menu_required_permission_code_fkey FOREIGN KEY (required_permission_code) REFERENCES public.iam_permission(permission_code);


--
-- Name: iam_notification_outbox iam_notification_outbox_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_notification_outbox
    ADD CONSTRAINT iam_notification_outbox_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.iam_tenant(id);


--
-- Name: iam_resource_acl iam_resource_acl_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_resource_acl
    ADD CONSTRAINT iam_resource_acl_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.iam_tenant(id);


--
-- Name: iam_role_menu iam_role_menu_menu_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_role_menu
    ADD CONSTRAINT iam_role_menu_menu_id_fkey FOREIGN KEY (menu_id) REFERENCES public.iam_menu(id);


--
-- Name: iam_role_menu iam_role_menu_role_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_role_menu
    ADD CONSTRAINT iam_role_menu_role_id_fkey FOREIGN KEY (role_id) REFERENCES public.iam_role(id);


--
-- Name: iam_role_menu iam_role_menu_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_role_menu
    ADD CONSTRAINT iam_role_menu_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.iam_tenant(id);


--
-- Name: iam_role_permission iam_role_permission_permission_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_role_permission
    ADD CONSTRAINT iam_role_permission_permission_id_fkey FOREIGN KEY (permission_id) REFERENCES public.iam_permission(id);


--
-- Name: iam_role_permission iam_role_permission_role_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_role_permission
    ADD CONSTRAINT iam_role_permission_role_id_fkey FOREIGN KEY (role_id) REFERENCES public.iam_role(id);


--
-- Name: iam_role_permission iam_role_permission_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_role_permission
    ADD CONSTRAINT iam_role_permission_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.iam_tenant(id);


--
-- Name: iam_role iam_role_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_role
    ADD CONSTRAINT iam_role_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.iam_tenant(id);


--
-- Name: iam_tenant_feature iam_tenant_feature_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_tenant_feature
    ADD CONSTRAINT iam_tenant_feature_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.iam_tenant(id);


--
-- Name: iam_user iam_user_department_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_user
    ADD CONSTRAINT iam_user_department_id_fkey FOREIGN KEY (department_id) REFERENCES public.iam_department(id);


--
-- Name: iam_user_invitation iam_user_invitation_invited_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_user_invitation
    ADD CONSTRAINT iam_user_invitation_invited_by_fkey FOREIGN KEY (invited_by) REFERENCES public.iam_user(id);


--
-- Name: iam_user_invitation iam_user_invitation_role_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_user_invitation
    ADD CONSTRAINT iam_user_invitation_role_id_fkey FOREIGN KEY (role_id) REFERENCES public.iam_role(id);


--
-- Name: iam_user_invitation iam_user_invitation_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_user_invitation
    ADD CONSTRAINT iam_user_invitation_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.iam_tenant(id);


--
-- Name: iam_user_invitation iam_user_invitation_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_user_invitation
    ADD CONSTRAINT iam_user_invitation_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.iam_user(id);


--
-- Name: iam_user_role iam_user_role_role_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_user_role
    ADD CONSTRAINT iam_user_role_role_id_fkey FOREIGN KEY (role_id) REFERENCES public.iam_role(id);


--
-- Name: iam_user_role iam_user_role_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_user_role
    ADD CONSTRAINT iam_user_role_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.iam_tenant(id);


--
-- Name: iam_user_role iam_user_role_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_user_role
    ADD CONSTRAINT iam_user_role_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.iam_user(id);


--
-- Name: iam_user iam_user_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.iam_user
    ADD CONSTRAINT iam_user_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.iam_tenant(id);


--
-- Name: kb_citation_record kb_citation_record_chat_message_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_citation_record
    ADD CONSTRAINT kb_citation_record_chat_message_id_fkey FOREIGN KEY (chat_message_id) REFERENCES public.app_chat_message(id);


--
-- Name: kb_citation_record kb_citation_record_chunk_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_citation_record
    ADD CONSTRAINT kb_citation_record_chunk_id_fkey FOREIGN KEY (chunk_id) REFERENCES public.kb_document_chunk(id);


--
-- Name: kb_citation_record kb_citation_record_document_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_citation_record
    ADD CONSTRAINT kb_citation_record_document_id_fkey FOREIGN KEY (document_id) REFERENCES public.kb_document(id);


--
-- Name: kb_citation_record kb_citation_record_knowledge_base_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_citation_record
    ADD CONSTRAINT kb_citation_record_knowledge_base_id_fkey FOREIGN KEY (knowledge_base_id) REFERENCES public.kb_knowledge_base(id);


--
-- Name: kb_citation_record kb_citation_record_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_citation_record
    ADD CONSTRAINT kb_citation_record_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.iam_tenant(id);


--
-- Name: kb_document_chunk kb_document_chunk_document_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_document_chunk
    ADD CONSTRAINT kb_document_chunk_document_id_fkey FOREIGN KEY (document_id) REFERENCES public.kb_document(id);


--
-- Name: kb_document_chunk_embedding kb_document_chunk_embedding_chunk_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_document_chunk_embedding
    ADD CONSTRAINT kb_document_chunk_embedding_chunk_id_fkey FOREIGN KEY (chunk_id) REFERENCES public.kb_document_chunk(id);


--
-- Name: kb_document_chunk_embedding kb_document_chunk_embedding_document_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_document_chunk_embedding
    ADD CONSTRAINT kb_document_chunk_embedding_document_id_fkey FOREIGN KEY (document_id) REFERENCES public.kb_document(id);


--
-- Name: kb_document_chunk_embedding kb_document_chunk_embedding_knowledge_base_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_document_chunk_embedding
    ADD CONSTRAINT kb_document_chunk_embedding_knowledge_base_id_fkey FOREIGN KEY (knowledge_base_id) REFERENCES public.kb_knowledge_base(id);


--
-- Name: kb_document_chunk_embedding kb_document_chunk_embedding_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_document_chunk_embedding
    ADD CONSTRAINT kb_document_chunk_embedding_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.iam_tenant(id);


--
-- Name: kb_document_chunk kb_document_chunk_knowledge_base_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_document_chunk
    ADD CONSTRAINT kb_document_chunk_knowledge_base_id_fkey FOREIGN KEY (knowledge_base_id) REFERENCES public.kb_knowledge_base(id);


--
-- Name: kb_document_chunk kb_document_chunk_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_document_chunk
    ADD CONSTRAINT kb_document_chunk_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.iam_tenant(id);


--
-- Name: kb_document kb_document_knowledge_base_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_document
    ADD CONSTRAINT kb_document_knowledge_base_id_fkey FOREIGN KEY (knowledge_base_id) REFERENCES public.kb_knowledge_base(id);


--
-- Name: kb_document_object_cleanup kb_document_object_cleanup_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_document_object_cleanup
    ADD CONSTRAINT kb_document_object_cleanup_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.iam_tenant(id);


--
-- Name: kb_document kb_document_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_document
    ADD CONSTRAINT kb_document_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.iam_tenant(id);


--
-- Name: kb_embedding_job kb_embedding_job_document_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_embedding_job
    ADD CONSTRAINT kb_embedding_job_document_id_fkey FOREIGN KEY (document_id) REFERENCES public.kb_document(id);


--
-- Name: kb_embedding_job kb_embedding_job_knowledge_base_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_embedding_job
    ADD CONSTRAINT kb_embedding_job_knowledge_base_id_fkey FOREIGN KEY (knowledge_base_id) REFERENCES public.kb_knowledge_base(id);


--
-- Name: kb_embedding_job kb_embedding_job_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_embedding_job
    ADD CONSTRAINT kb_embedding_job_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.iam_tenant(id);


--
-- Name: kb_knowledge_base kb_knowledge_base_embedding_model_config_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_knowledge_base
    ADD CONSTRAINT kb_knowledge_base_embedding_model_config_id_fkey FOREIGN KEY (embedding_model_config_id) REFERENCES public.mdl_config(id);


--
-- Name: kb_knowledge_base kb_knowledge_base_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_knowledge_base
    ADD CONSTRAINT kb_knowledge_base_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.iam_tenant(id);


--
-- Name: kb_knowledge_index_active kb_knowledge_index_active_knowledge_base_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_knowledge_index_active
    ADD CONSTRAINT kb_knowledge_index_active_knowledge_base_id_fkey FOREIGN KEY (knowledge_base_id) REFERENCES public.kb_knowledge_base(id);


--
-- Name: kb_knowledge_index_active kb_knowledge_index_active_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_knowledge_index_active
    ADD CONSTRAINT kb_knowledge_index_active_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.iam_tenant(id);


--
-- Name: kb_knowledge_index_desired kb_knowledge_index_desired_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_knowledge_index_desired
    ADD CONSTRAINT kb_knowledge_index_desired_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.iam_tenant(id);


--
-- Name: kb_knowledge_index_rebuild_member kb_knowledge_index_rebuild_member_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_knowledge_index_rebuild_member
    ADD CONSTRAINT kb_knowledge_index_rebuild_member_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.iam_tenant(id);


--
-- Name: kb_knowledge_index_rebuild kb_knowledge_index_rebuild_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_knowledge_index_rebuild
    ADD CONSTRAINT kb_knowledge_index_rebuild_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.iam_tenant(id);


--
-- Name: kb_model_usage_policy_projection kb_model_usage_policy_projection_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_model_usage_policy_projection
    ADD CONSTRAINT kb_model_usage_policy_projection_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.iam_tenant(id);


--
-- Name: mdl_approval_decision_inbox mdl_approval_decision_inbox_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mdl_approval_decision_inbox
    ADD CONSTRAINT mdl_approval_decision_inbox_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.iam_tenant(id);


--
-- Name: mdl_config mdl_config_provider_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mdl_config
    ADD CONSTRAINT mdl_config_provider_id_fkey FOREIGN KEY (provider_id) REFERENCES public.mdl_provider(id);


--
-- Name: mdl_config mdl_config_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mdl_config
    ADD CONSTRAINT mdl_config_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.iam_tenant(id);


--
-- Name: mdl_invocation_log mdl_invocation_log_model_config_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mdl_invocation_log
    ADD CONSTRAINT mdl_invocation_log_model_config_id_fkey FOREIGN KEY (model_config_id) REFERENCES public.mdl_config(id);


--
-- Name: mdl_invocation_log mdl_invocation_log_provider_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mdl_invocation_log
    ADD CONSTRAINT mdl_invocation_log_provider_id_fkey FOREIGN KEY (provider_id) REFERENCES public.mdl_provider(id);


--
-- Name: mdl_invocation_log mdl_invocation_log_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mdl_invocation_log
    ADD CONSTRAINT mdl_invocation_log_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.iam_tenant(id);


--
-- Name: mdl_provider mdl_provider_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mdl_provider
    ADD CONSTRAINT mdl_provider_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.iam_tenant(id);


--
-- Name: mdl_secret mdl_secret_provider_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mdl_secret
    ADD CONSTRAINT mdl_secret_provider_id_fkey FOREIGN KEY (provider_id) REFERENCES public.mdl_provider(id);


--
-- Name: mdl_secret mdl_secret_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mdl_secret
    ADD CONSTRAINT mdl_secret_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.iam_tenant(id);


--
-- PostgreSQL database dump complete
--
