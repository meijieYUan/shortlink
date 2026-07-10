package com.shortlink.openapi.auth;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_api_key")
public class ApiKeyEntity {

    @TableId
    private String appKey;

    private String appSecret;
    private String owner;
    private Integer status;

    /** Comma-separated IP/CIDR whitelist. Null or empty = allow all. */
    private String ipWhitelist;

    private Integer quotaPerMinute;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}