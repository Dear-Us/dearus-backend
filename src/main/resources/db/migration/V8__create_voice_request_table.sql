CREATE TABLE voice_request (
    voice_request_id BIGINT NOT NULL AUTO_INCREMENT,
    baby_id BIGINT NOT NULL,
    requester_member_id BIGINT NOT NULL,
    idempotency_key VARCHAR(36) NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    processing_status VARCHAR(20) NOT NULL,
    result_status VARCHAR(24) NULL,
    transcript TEXT NULL,
    action VARCHAR(32) NULL,
    draft_json TEXT NULL,
    care_record_id BIGINT NULL,
    failure_reason VARCHAR(40) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_voice_request PRIMARY KEY (voice_request_id),
    CONSTRAINT uk_voice_request_member_key UNIQUE (requester_member_id, idempotency_key),
    CONSTRAINT fk_voice_request_baby FOREIGN KEY (baby_id) REFERENCES baby (baby_id),
    CONSTRAINT fk_voice_request_member FOREIGN KEY (requester_member_id) REFERENCES member (member_id)
);

CREATE INDEX idx_voice_request_baby_member ON voice_request (baby_id, requester_member_id);
