package com.shortlink.core.model.entity;

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
@TableName("t_short_link")
public class ShortLink {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** Short code, e.g. "aB3x9Kq" */
    private String shortCode;

    /** Original long URL */
    private String originalUrl;

    /** Expiration time; null means never expires */
    private LocalDateTime expireTime;

    /** 1=active, 0=disabled */
    private Integer status;

    private String creator;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}