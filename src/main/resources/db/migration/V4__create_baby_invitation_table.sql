CREATE TABLE baby_invitation (
    invitation_id BIGINT NOT NULL AUTO_INCREMENT,
    baby_id BIGINT NOT NULL,
    created_by_member_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL,
    role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    accepted_by_member_id BIGINT NULL,
    accepted_at DATETIME(6) NULL,
    canceled_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_baby_invitation PRIMARY KEY (invitation_id),
    CONSTRAINT uk_baby_invitation_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_baby_invitation_baby FOREIGN KEY (baby_id) REFERENCES baby (baby_id),
    CONSTRAINT fk_baby_invitation_creator FOREIGN KEY (created_by_member_id) REFERENCES member (member_id)
);

CREATE INDEX idx_baby_invitation_baby ON baby_invitation (baby_id);
