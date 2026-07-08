CREATE DATABASE IF NOT EXISTS shortlink DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE shortlink;

CREATE TABLE IF NOT EXISTS t_short_link (
    id            BIGINT PRIMARY KEY COMMENT '由号段发号器分配',
    short_code    VARCHAR(8)    NOT NULL UNIQUE COMMENT '短码',
    original_url  TEXT          NOT NULL COMMENT '原始链接',
    url_hash      VARCHAR(32)   NOT NULL COMMENT 'MD5哈希(幂等查重用)',
    expire_time   DATETIME      DEFAULT NULL COMMENT '过期时间',
    status        TINYINT       DEFAULT 1 COMMENT '1:有效 0:失效',
    creator       VARCHAR(64)   DEFAULT '' COMMENT '创建者',
    create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_short_code (short_code),
    INDEX idx_url_hash (url_hash),
    INDEX idx_status_expire (status, expire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='短链接映射表';

CREATE TABLE IF NOT EXISTS t_id_segment (
    biz_tag     VARCHAR(64) PRIMARY KEY COMMENT '业务标识',
    max_id      BIGINT NOT NULL DEFAULT 0 COMMENT '当前已分配最大ID',
    step        INT    NOT NULL DEFAULT 2000 COMMENT '号段步长',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='号段发号器表';

INSERT INTO t_id_segment (biz_tag, max_id, step) VALUES ('short_link', 0, 2000)
ON DUPLICATE KEY UPDATE biz_tag = biz_tag;