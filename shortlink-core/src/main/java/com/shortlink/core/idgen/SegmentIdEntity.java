package com.shortlink.core.idgen;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Entity mapped to t_id_segment — stores the current segment allocation state.
 */
@Data
@TableName("t_id_segment")
public class SegmentIdEntity {

    @TableId
    private String bizTag;

    /** Current max ID allocated */
    private Long maxId;

    /** Number of IDs to allocate per segment */
    private Integer step;

    private LocalDateTime updateTime;
}