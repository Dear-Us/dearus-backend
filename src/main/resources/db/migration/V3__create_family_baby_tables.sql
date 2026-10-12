CREATE TABLE baby (
    baby_id BIGINT NOT NULL AUTO_INCREMENT,
    created_by_member_id BIGINT NOT NULL,
    baby_name VARCHAR(50) NOT NULL,
    birth_date DATE NOT NULL,
    gender VARCHAR(10) NOT NULL,
    profile_image_url VARCHAR(2048) NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    CONSTRAINT pk_baby PRIMARY KEY (baby_id),
    CONSTRAINT fk_baby_creator FOREIGN KEY (created_by_member_id) REFERENCES member (member_id)
);

CREATE TABLE baby_member (
    baby_member_id BIGINT NOT NULL AUTO_INCREMENT,
    baby_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    joined_at DATETIME(6) NOT NULL,
    ended_at DATETIME(6) NULL,
    CONSTRAINT pk_baby_member PRIMARY KEY (baby_member_id),
    CONSTRAINT uk_baby_member_baby_member UNIQUE (baby_id, member_id),
    CONSTRAINT fk_baby_member_baby FOREIGN KEY (baby_id) REFERENCES baby (baby_id),
    CONSTRAINT fk_baby_member_member FOREIGN KEY (member_id) REFERENCES member (member_id)
);

CREATE INDEX idx_baby_member_member ON baby_member (member_id);
