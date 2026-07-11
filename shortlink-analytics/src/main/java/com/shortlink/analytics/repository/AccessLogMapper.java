package com.shortlink.analytics.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortlink.analytics.model.AccessLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AccessLogMapper extends BaseMapper<AccessLog> {

    /** 脚本是 MyBatis 中使用注解方式实现动态批量插入
     * Batch insert access logs for high-throughput persistence */
    @Insert("<script>" +
        "INSERT INTO t_access_log (short_code, ip, user_agent, referer, access_time) VALUES " +
        "<foreach collection='list' item='item' separator=','>" +
        "(#{item.shortCode}, #{item.ip}, #{item.userAgent}, #{item.referer}, #{item.accessTime})" +
        "</foreach>" +
        "</script>")
    int batchInsert(@Param("list") List<AccessLog> list);
}