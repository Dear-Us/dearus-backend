ALTER TABLE care_record
    ADD COLUMN active_baby_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN status = 'IN_PROGRESS' AND is_deleted = FALSE THEN baby_id ELSE NULL END
    );

CREATE UNIQUE INDEX uk_care_record_active_baby ON care_record (active_baby_id);
