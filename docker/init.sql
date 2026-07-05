CREATE DATABASE IF NOT EXISTS shortlink DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE shortlink;

CREATE TABLE IF NOT EXISTS t_short_link (
    id            BIGINT PRIMARY KEY,
    short_code    VARCHAR(8)    NOT NULL UNIQUE COMMENT '??',
    original_url  TEXT          NOT NULL COMMENT '????',
    expire_time   DATETIME      DEFAULT NULL COMMENT '????',
    status        TINYINT       DEFAULT 1 COMMENT '1:?? 0:??',
    creator       VARCHAR(64)   DEFAULT '' COMMENT '???',
    create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_short_code (short_code),
    INDEX idx_status_expire (status, expire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='??????';

CREATE TABLE IF NOT EXISTS t_id_segment (
    biz_tag     VARCHAR(64) PRIMARY KEY COMMENT '????',
    max_id      BIGINT NOT NULL DEFAULT 0 COMMENT '???????ID',
    step        INT    NOT NULL DEFAULT 2000 COMMENT '????',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='??????';

INSERT INTO t_id_segment (biz_tag, max_id, step) VALUES ('short_link', 0, 2000)
ON DUPLICATE KEY UPDATE biz_tag = biz_tag;
