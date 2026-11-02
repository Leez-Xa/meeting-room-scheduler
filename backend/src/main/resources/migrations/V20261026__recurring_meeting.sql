-- 周期增量迁移，查询元数据后按需补列、索引和外键，可重复执行。
-- MySQL DDL 不应当作普通业务事务整体回滚，部署前须备份并确认目标库。
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

SET @series_column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'meeting_book' AND column_name = 'series_id'
);
SET @series_column_sql = IF(@series_column_exists = 0,
    'ALTER TABLE meeting_book ADD COLUMN series_id BIGINT NULL', 'SELECT 1');
PREPARE series_column_stmt FROM @series_column_sql;
EXECUTE series_column_stmt;
DEALLOCATE PREPARE series_column_stmt;

SET @series_column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'meeting_book' AND column_name = 'series_index'
);
SET @series_column_sql = IF(@series_column_exists = 0,
    'ALTER TABLE meeting_book ADD COLUMN series_index INT NULL', 'SELECT 1');
PREPARE series_column_stmt FROM @series_column_sql;
EXECUTE series_column_stmt;
DEALLOCATE PREPARE series_column_stmt;

SET @series_column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'meeting_book' AND column_name = 'series_total'
);
SET @series_column_sql = IF(@series_column_exists = 0,
    'ALTER TABLE meeting_book ADD COLUMN series_total INT NULL', 'SELECT 1');
PREPARE series_column_stmt FROM @series_column_sql;
EXECUTE series_column_stmt;
DEALLOCATE PREPARE series_column_stmt;

SET @series_index_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'meeting_book' AND index_name = 'idx_book_series'
);
SET @series_index_sql = IF(@series_index_exists = 0,
    'ALTER TABLE meeting_book ADD INDEX idx_book_series (series_id, series_index, status, deleted)',
    'SELECT 1');
PREPARE series_index_stmt FROM @series_index_sql;
EXECUTE series_index_stmt;
DEALLOCATE PREPARE series_index_stmt;

SET @series_fk_exists = (
    SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE() AND table_name = 'meeting_book' AND constraint_name = 'fk_book_series'
);
SET @series_fk_sql = IF(@series_fk_exists = 0,
    'ALTER TABLE meeting_book ADD CONSTRAINT fk_book_series FOREIGN KEY (series_id) REFERENCES meeting_series(id)',
    'SELECT 1');
PREPARE series_fk_stmt FROM @series_fk_sql;
EXECUTE series_fk_stmt;
DEALLOCATE PREPARE series_fk_stmt;
