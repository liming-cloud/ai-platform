ALTER TABLE trusted_query.query_run_event
    ADD CONSTRAINT ck_query_run_event_terminal_sequence
        CHECK (
            event_type = 'QUERY_STARTED'
            OR sequence > 1
        );
