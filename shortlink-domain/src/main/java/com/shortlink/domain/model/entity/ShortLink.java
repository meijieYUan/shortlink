package com.shortlink.domain.model.entity;

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

    @TableId(type = IdType.INPUT)
    private Long id;
    private String shortCode;
    private String originalUrl;
    private String urlHash;
    private String appKey;
    private LocalDateTime expireTime;
    private Integer status;
    private String creator;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}