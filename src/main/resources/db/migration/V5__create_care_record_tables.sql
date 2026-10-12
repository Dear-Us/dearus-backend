CREATE TABLE care_record (
    record_id BIGINT NOT NULL AUTO_INCREMENT,
    baby_id BIGINT NOT NULL,
    record_type VARCHAR(20) NOT NULL,
    input_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    recorded_at DATETIME(6) NOT NULL,
    start_at DATETIME(6) NULL,
    end_at DATETIME(6) NULL,
    memo TEXT NULL,
    created_by_member_id BIGINT NOT NULL,
    completed_by_member_id BIGINT NULL,
    last_updated_by_member_id BIGINT NOT NULL,
    deleted_by_member_id BIGINT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    CONSTRAINT pk_care_record PRIMARY KEY (record_id),
    CONSTRAINT fk_care_record_baby FOREIGN KEY (baby_id) REFERENCES baby (baby_id)
);

CREATE INDEX idx_care_record_baby_recorded ON care_record (baby_id, recorded_at);

CREATE TABLE feeding_detail (
    record_id BIGINT NOT NULL,
    feeding_type VARCHAR(30) NOT NULL,
    amount_ml INT NULL,
    breast_side VARCHAR(10) NULL,
    duration_minutes INT NULL,
    CONSTRAINT pk_feeding_detail PRIMARY KEY (record_id),
    CONSTRAINT fk_feeding_detail_record FOREIGN KEY (record_id) REFERENCES care_record (record_id)
);

CREATE TABLE diaper_detail (
    record_id BIGINT NOT NULL,
    diaper_type VARCHAR(10) NOT NULL,
    `condition` TEXT NULL,
    color TEXT NULL,
    CONSTRAINT pk_diaper_detail PRIMARY KEY (record_id),
    CONSTRAINT fk_diaper_detail_record FOREIGN KEY (record_id) REFERENCES care_record (record_id)
);

CREATE TABLE temperature_detail (
    record_id BIGINT NOT NULL,
    temperature_celsius DECIMAL(65, 30) NOT NULL,
    measured_part VARCHAR(20) NOT NULL,
    CONSTRAINT pk_temperature_detail PRIMARY KEY (record_id),
    CONSTRAINT fk_temperature_detail_record FOREIGN KEY (record_id) REFERENCES care_record (record_id)
);

CREATE TABLE medicine_detail (
    record_id BIGINT NOT NULL,
    medicine_name VARCHAR(100) NOT NULL,
    dose_value DECIMAL(65, 30) NOT NULL,
    dose_unit VARCHAR(20) NOT NULL,
    CONSTRAINT pk_medicine_detail PRIMARY KEY (record_id),
    CONSTRAINT fk_medicine_detail_record FOREIGN KEY (record_id) REFERENCES care_record (record_id)
);

CREATE TABLE other_detail (
    record_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    CONSTRAINT pk_other_detail PRIMARY KEY (record_id),
    CONSTRAINT fk_other_detail_record FOREIGN KEY (record_id) REFERENCES care_record (record_id)
);
