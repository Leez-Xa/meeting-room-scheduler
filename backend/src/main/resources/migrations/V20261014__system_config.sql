-- Additive upgrade for an existing database; does not change existing business records.
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
