package com.shortlink.core.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shortlink.core.model.entity.ShortLink;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ShortLinkMapper extends BaseMapper<ShortLink> {
}