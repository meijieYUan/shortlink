package com.shortlink.core.idgen;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * MyBatis-Plus mapper for t_id_segment.
 */
@Mapper
public interface SegmentIdMapper extends BaseMapper<SegmentIdEntity> {

    /**
     * Atomically allocate a segment by incrementing max_id.
     * UPDATE is atomic across connections, ensuring no two instances get the same range.
     */
    @Update("UPDATE t_id_segment SET max_id = max_id + step, update_time = NOW() WHERE biz_tag = #{bizTag}")
    int allocateSegment(@Param("bizTag") String bizTag);
}