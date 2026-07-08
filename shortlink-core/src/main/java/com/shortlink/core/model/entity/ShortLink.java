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

    /** ID provided by IdGenerator (segment mode), not DB auto-increment */
    @TableId(type = IdType.INPUT)
    private Long id;

    private String shortCode;
    private String originalUrl;
    private String urlHash;
    private LocalDateTime expireTime;

    /** 1=active, 0=disabled */
    private Integer status;

    private String creator;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}