CREATE TABLE growth_record (
    growth_record_id BIGINT NOT NULL AUTO_INCREMENT,
    baby_id BIGINT NOT NULL,
    measured_date DATE NOT NULL,
    height_cm DECIMAL(65, 30) NULL,
    weight_kg DECIMAL(65, 30) NULL,
    memo TEXT NULL,
    created_by_member_id BIGINT NOT NULL,
    last_updated_by_member_id BIGINT NOT NULL,
    deleted_by_member_id BIGINT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    CONSTRAINT pk_growth_record PRIMARY KEY (growth_record_id),
    CONSTRAINT fk_growth_record_baby FOREIGN KEY (baby_id) REFERENCES baby (baby_id)
);

CREATE INDEX idx_growth_record_baby_date_id ON growth_record (baby_id, measured_date, growth_record_id);
