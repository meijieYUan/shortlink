package com.shortlink.domain.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shortlink.domain.model.entity.ShortLink;

import java.time.LocalDateTime;
import java.util.Map;

public interface OpenApiLinkService extends CoreLinkService {

    /** Tenant-scoped: paginated list of links. */
    Page<ShortLink> listLinks(int page, int size, Integer status, String keyword);

    /** Tenant-scoped: get one link by short code. */
    ShortLink getLink(String shortCode);

    /** Tenant-scoped: update link status / expire time. */
    ShortLink updateLink(String shortCode, Integer status, String customCode, LocalDateTime expireTime);

    /** Tenant-scoped: soft-delete to recycle bin. */
    void softDelete(String shortCode);

    /** Tenant-scoped: restore from recycle bin. */
    void restore(String shortCode);

    /** Tenant-scoped: stats overview (total / active / recycled). */
    Map<String, Object> getStats();

    /** Tenant-scoped: count of links created today. */
    long countTodayLinks();
}