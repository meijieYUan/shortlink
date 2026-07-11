package com.shortlink.analytics.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_access_log")
public class AccessLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String shortCode;
    private String ip;
    private String userAgent;
    private String referer;
    private LocalDateTime accessTime;
}