ALTER TABLE assistant_delivery.assistant_request
    DROP CONSTRAINT ck_assistant_request_operation,
    DROP CONSTRAINT ck_assistant_request_result;

ALTER TABLE assistant_delivery.assistant_request
    ADD CONSTRAINT ck_assistant_request_operation
        CHECK (
            operation IN (
                'CREATE_ASSISTANT',
                'CREATE_VERSION',
                'PUBLISH_VERSION'
            )
        ),
    ADD CONSTRAINT ck_assistant_request_result
        CHECK (
            (operation = 'CREATE_ASSISTANT' AND assistant_version_id IS NULL)
            OR (
                operation IN ('CREATE_VERSION', 'PUBLISH_VERSION')
                AND assistant_version_id IS NOT NULL
            )
        );
