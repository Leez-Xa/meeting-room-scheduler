CREATE TABLE IF NOT EXISTS dept (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dept_name VARCHAR(100) NOT NULL,
    parent_id BIGINT NULL,
    sort_no INT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1,
    create_by BIGINT NULL,
    update_by BIGINT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    real_name VARCHAR(50) NOT NULL,
    password VARCHAR(100) NOT NULL,
    dept_id BIGINT NOT NULL,
    role VARCHAR(30) NOT NULL,
    phone VARCHAR(20),
    email VARCHAR(100),
    floor_no INT NOT NULL DEFAULT 1,
    status TINYINT NOT NULL DEFAULT 1,
    create_by BIGINT NULL,
    update_by BIGINT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_user_dept FOREIGN KEY (dept_id) REFERENCES dept(id)
);

CREATE TABLE IF NOT EXISTS meeting_room (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_name VARCHAR(100) NOT NULL,
    floor_no INT NOT NULL,
    capacity INT NOT NULL,
    location VARCHAR(255) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    create_by BIGINT NULL,
    update_by BIGINT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS device (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id BIGINT NOT NULL,
    device_name VARCHAR(100) NOT NULL,
    device_type VARCHAR(50) NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    status TINYINT NOT NULL DEFAULT 1,
    create_by BIGINT NULL,
    update_by BIGINT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_device_room FOREIGN KEY (room_id) REFERENCES meeting_room(id)
);

CREATE TABLE IF NOT EXISTS meeting_series (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    dept_id BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    subject VARCHAR(200) NOT NULL,
    attend_num INT NOT NULL,
    first_start_time DATETIME NOT NULL,
    first_end_time DATETIME NOT NULL,
    occurrence_count INT NOT NULL,
    create_by BIGINT NOT NULL,
    update_by BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_series_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_series_dept FOREIGN KEY (dept_id) REFERENCES dept(id),
    CONSTRAINT fk_series_room FOREIGN KEY (room_id) REFERENCES meeting_room(id),
    CONSTRAINT ck_series_count CHECK (occurrence_count BETWEEN 2 AND 12),
    INDEX idx_series_user (user_id, create_time, deleted)
);

CREATE TABLE IF NOT EXISTS meeting_book (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    dept_id BIGINT NOT NULL,
    series_id BIGINT NULL,
    series_index INT NULL,
    series_total INT NULL,
    applicant_role VARCHAR(30) NOT NULL DEFAULT 'EMPLOYEE',
    room_id BIGINT NULL,
    subject VARCHAR(200) NOT NULL,
    attend_num INT NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    status VARCHAR(30) NOT NULL,
    audit_user BIGINT NULL,
    audit_time DATETIME NULL,
    audit_comment VARCHAR(500) NULL,
    cancel_reason VARCHAR(500) NULL,
    create_by BIGINT NOT NULL,
    update_by BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_book_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_book_dept FOREIGN KEY (dept_id) REFERENCES dept(id),
    CONSTRAINT fk_book_series FOREIGN KEY (series_id) REFERENCES meeting_series(id),
    CONSTRAINT fk_book_room FOREIGN KEY (room_id) REFERENCES meeting_room(id),
    CONSTRAINT fk_book_auditor FOREIGN KEY (audit_user) REFERENCES sys_user(id),
    CONSTRAINT ck_book_status CHECK (status IN ('DRAFT','PENDING','APPROVED','IN_PROGRESS','COMPLETED','CANCELED','REJECTED','EXPIRED')),
    INDEX idx_book_room_time (room_id, start_time, end_time, status, deleted),
    INDEX idx_book_user_time (user_id, start_time, end_time, status, deleted),
    INDEX idx_book_series (series_id, series_index, status, deleted)
);

CREATE TABLE IF NOT EXISTS meeting_attendee (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    book_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by BIGINT NULL,
    update_by BIGINT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_book_attendee UNIQUE (book_id, user_id),
    CONSTRAINT fk_attendee_book FOREIGN KEY (book_id) REFERENCES meeting_book(id),
    CONSTRAINT fk_attendee_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
    INDEX idx_attendee_user (user_id, book_id)
);

CREATE TABLE IF NOT EXISTS meeting_book_device (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    book_id BIGINT NOT NULL,
    device_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    create_by BIGINT NULL,
    update_by BIGINT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_actual_book_device UNIQUE (book_id, device_id),
    CONSTRAINT fk_actual_book_device_book FOREIGN KEY (book_id) REFERENCES meeting_book(id),
    CONSTRAINT fk_actual_book_device_device FOREIGN KEY (device_id) REFERENCES device(id)
);

CREATE TABLE IF NOT EXISTS meeting_device_requirement (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    book_id BIGINT NOT NULL,
    device_type VARCHAR(50) NOT NULL,
    quantity INT NOT NULL,
    create_by BIGINT NULL,
    update_by BIGINT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_book_device UNIQUE (book_id, device_type),
    CONSTRAINT fk_book_device_book FOREIGN KEY (book_id) REFERENCES meeting_book(id)
);

CREATE TABLE IF NOT EXISTS approval_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    book_id BIGINT NOT NULL,
    operator_id BIGINT NOT NULL,
    action VARCHAR(30) NOT NULL,
    comment VARCHAR(500),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by BIGINT NULL,
    update_by BIGINT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_approval_book FOREIGN KEY (book_id) REFERENCES meeting_book(id),
    CONSTRAINT fk_approval_operator FOREIGN KEY (operator_id) REFERENCES sys_user(id)
);

CREATE TABLE IF NOT EXISTS audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    operator_id BIGINT NOT NULL,
    operate_type VARCHAR(50) NOT NULL,
    content VARCHAR(1000) NOT NULL,
    operate_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by BIGINT NULL,
    update_by BIGINT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_audit_operator FOREIGN KEY (operator_id) REFERENCES sys_user(id)
);

CREATE TABLE IF NOT EXISTS message_notice (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    book_id BIGINT NULL,
    title VARCHAR(200) NOT NULL,
    content VARCHAR(500) NOT NULL,
    readed TINYINT NOT NULL DEFAULT 0,
    notice_key VARCHAR(150) NOT NULL UNIQUE,
    create_by BIGINT NULL,
    update_by BIGINT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_notice_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_notice_book FOREIGN KEY (book_id) REFERENCES meeting_book(id)
);

CREATE TABLE IF NOT EXISTS system_config (
    id BIGINT PRIMARY KEY,
    revision BIGINT NOT NULL DEFAULT 0,
    settings_json TEXT NULL,
    create_by BIGINT NULL,
    update_by BIGINT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_config_create_user FOREIGN KEY (create_by) REFERENCES sys_user(id),
    CONSTRAINT fk_config_update_user FOREIGN KEY (update_by) REFERENCES sys_user(id)
);
INSERT INTO system_config(id,revision)
SELECT 1,0 WHERE NOT EXISTS (SELECT 1 FROM system_config WHERE id=1);
