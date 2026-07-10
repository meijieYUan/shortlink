package com.shortlink.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shortlink.cache.model.CacheLinkInfo;
import com.shortlink.cache.strategy.ShortLinkCacheService;
import com.shortlink.common.constant.Constants;
import com.shortlink.common.exception.BizException;
import com.shortlink.common.result.ResultCode;
import com.shortlink.common.util.Base62Util;
import com.shortlink.common.util.HashUtil;
import com.shortlink.common.util.UrlValidator;
import com.shortlink.core.idgen.IdGenerator;
import com.shortlink.core.model.dto.ShortenRequest;
import com.shortlink.core.model.dto.ShortenResponse;
import com.shortlink.core.model.entity.ShortLink;
import com.shortlink.core.repository.ShortLinkMapper;
import com.shortlink.core.service.ShortLinkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShortLinkServiceImpl implements ShortLinkService {

    private final ShortLinkMapper shortLinkMapper;
    private final ShortLinkCacheService cacheService;
    private final IdGenerator idGenerator;

    @Value("${shortlink.base-url:http://localhost:8080}")
    private String baseUrl;

    @Override
    @Transactional
    public ShortenResponse shorten(ShortenRequest request) {
        String normalizedUrl = UrlValidator.normalize(request.getOriginalUrl());
        if (!UrlValidator.isValid(normalizedUrl)) {
            throw new BizException(ResultCode.URL_INVALID);
        }

        String urlHash = HashUtil.md5Hex(normalizedUrl);

        // Idempotency check via hash
        List<ShortLink> candidates = shortLinkMapper.selectList(
            new LambdaQueryWrapper<ShortLink>()
                .eq(ShortLink::getUrlHash, urlHash)
                .eq(ShortLink::getStatus, 1)
                .and(w -> w.isNull(ShortLink::getExpireTime)
                           .or().gt(ShortLink::getExpireTime, LocalDateTime.now()))
        );
        for (ShortLink candidate : candidates) {
            if (normalizedUrl.equals(candidate.getOriginalUrl())) {
                log.info("Idempotent hit: shortCode={}", candidate.getShortCode());
                return buildResponse(candidate);
            }
        }

        // Custom short code or auto-generated
        String shortCode;
        Long id = null;
        if (request.getCustomCode() != null && !request.getCustomCode().isBlank()) {
            shortCode = request.getCustomCode();
            // Check collision
            ShortLink existing = shortLinkMapper.selectOne(
                new LambdaQueryWrapper<ShortLink>().eq(ShortLink::getShortCode, shortCode));
            if (existing != null) {
                throw new BizException(ResultCode.SHORT_CODE_CONFLICT,
                    "Custom short code '" + shortCode + "' is already taken. Try '"
                    + shortCode + "2' or '" + shortCode + "_'");
            }
        } else {
            id = idGenerator.nextId();
            shortCode = Base62Util.encode(id);
        }

        LocalDateTime expireTime = request.getExpireTime();
        if (expireTime == null) {
            expireTime = LocalDateTime.now().plusDays(Constants.DEFAULT_EXPIRE_DAYS);
        }

        ShortLink entity = ShortLink.builder()
            .id(id)
            .shortCode(shortCode)
            .originalUrl(normalizedUrl)
            .urlHash(urlHash)
            .expireTime(expireTime)
            .status(1)
            .creator("")
            .build();

        int rows = shortLinkMapper.insert(entity);
        if (rows <= 0) {
            throw new BizException(ResultCode.GENERATE_FAILED);
        }

        cacheService.onCreated(shortCode, toCacheInfo(entity));
        log.info("Short link created: shortCode={}, urlHash={}", shortCode, urlHash);
        return buildResponse(entity);
    }

    @Override
    public String getOriginalUrl(String shortCode) {
        Optional<CacheLinkInfo> result = cacheService.get(shortCode, () ->
            Optional.ofNullable(shortLinkMapper.selectOne(
                new LambdaQueryWrapper<ShortLink>().eq(ShortLink::getShortCode, shortCode)))
                .map(this::toCacheInfo));
        return result.map(CacheLinkInfo::getOriginalUrl).orElse(null);
    }

    @Override
    public boolean isExpired(String shortCode) {
        Optional<CacheLinkInfo> result = cacheService.get(shortCode, () -> {
            ShortLink entity = shortLinkMapper.selectOne(
                new LambdaQueryWrapper<ShortLink>().eq(ShortLink::getShortCode, shortCode));
            if (entity == null || entity.getStatus() != 1) return Optional.empty();
            return Optional.of(toCacheInfo(entity));
        });
        return result.map(info -> info.getExpireTime() != null
            && info.getExpireTime().isBefore(LocalDateTime.now())).orElse(false);
    }

    // ---- Admin operations ----

    public Page<ShortLink> listLinks(int page, int size, Integer status, String keyword) {
        LambdaQueryWrapper<ShortLink> qw = new LambdaQueryWrapper<>();
        if (status != null) qw.eq(ShortLink::getStatus, status);
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like(ShortLink::getShortCode, keyword)
                         .or().like(ShortLink::getOriginalUrl, keyword));
        }
        qw.orderByDesc(ShortLink::getCreateTime);
        return shortLinkMapper.selectPage(new Page<>(page, size), qw);
    }

    public ShortLink getLink(String shortCode) {
        return shortLinkMapper.selectOne(
            new LambdaQueryWrapper<ShortLink>().eq(ShortLink::getShortCode, shortCode));
    }

    @Transactional
    public ShortLink updateLink(String shortCode, Integer newStatus, String newCustomCode,
                                 LocalDateTime newExpireTime) {
        ShortLink entity = shortLinkMapper.selectOne(
            new LambdaQueryWrapper<ShortLink>().eq(ShortLink::getShortCode, shortCode));
        if (entity == null) throw new IllegalArgumentException("Short code not found: " + shortCode);

        if (newStatus != null) entity.setStatus(newStatus);
        if (newCustomCode != null && !newCustomCode.isBlank()) {
            ShortLink conflict = shortLinkMapper.selectOne(
                new LambdaQueryWrapper<ShortLink>().eq(ShortLink::getShortCode, newCustomCode));
            if (conflict != null && !conflict.getId().equals(entity.getId())) {
                throw new BizException(ResultCode.SHORT_CODE_CONFLICT);
            }
            entity.setShortCode(newCustomCode);
        }
        if (newExpireTime != null) entity.setExpireTime(newExpireTime);

        shortLinkMapper.updateById(entity);
        cacheService.onUpdated(shortCode);
        log.info("Link updated: shortCode={}, status={}", shortCode, newStatus);
        return entity;
    }

    @Transactional
    public void softDelete(String shortCode) {
        ShortLink entity = shortLinkMapper.selectOne(
            new LambdaQueryWrapper<ShortLink>().eq(ShortLink::getShortCode, shortCode));
        if (entity == null) return;
        entity.setStatus(2); // recycle bin
        shortLinkMapper.updateById(entity);
        cacheService.onUpdated(shortCode);
        log.info("Link soft-deleted to recycle bin: shortCode={}", shortCode);
    }

    @Transactional
    public void restore(String shortCode) {
        ShortLink entity = shortLinkMapper.selectOne(
            new LambdaQueryWrapper<ShortLink>().eq(ShortLink::getShortCode, shortCode));
        if (entity == null || entity.getStatus() != 2) {
            throw new IllegalArgumentException("Link not in recycle bin: " + shortCode);
        }
        entity.setStatus(1);
        shortLinkMapper.updateById(entity);
        cacheService.onUpdated(shortCode);
        log.info("Link restored from recycle bin: shortCode={}", shortCode);
    }

    @Transactional
    public int cleanRecycleBin(int retentionDays) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        List<ShortLink> expired = shortLinkMapper.selectList(
            new LambdaQueryWrapper<ShortLink>()
                .eq(ShortLink::getStatus, 2)
                .le(ShortLink::getUpdateTime, cutoff));
        for (ShortLink link : expired) {
            shortLinkMapper.deleteById(link.getId());
        }
        if (!expired.isEmpty()) log.info("Recycle bin cleanup: {} links permanently deleted", expired.size());
        return expired.size();
    }

    // ---- Scheduled tasks ----

    public int expireLinks() {
        List<ShortLink> expired = shortLinkMapper.selectList(
            new LambdaQueryWrapper<ShortLink>()
                .eq(ShortLink::getStatus, 1)
                .le(ShortLink::getExpireTime, LocalDateTime.now()));
        for (ShortLink link : expired) {
            link.setStatus(0);
            shortLinkMapper.updateById(link);
            cacheService.onUpdated(link.getShortCode());
        }
        if (!expired.isEmpty()) log.info("Expired {} short links", expired.size());
        return expired.size();
    }

    // ---- Dashboard stats ----

    public java.util.Map<String, Object> getStats() {
        long total = shortLinkMapper.selectCount(null);
        long active = shortLinkMapper.selectCount(
            new LambdaQueryWrapper<ShortLink>().eq(ShortLink::getStatus, 1));
        long recycled = shortLinkMapper.selectCount(
            new LambdaQueryWrapper<ShortLink>().eq(ShortLink::getStatus, 2));
        return java.util.Map.of("total", total, "active", active, "recycled", recycled);
    }

    // ---- Helpers ----

    private ShortenResponse buildResponse(ShortLink entity) {
        return ShortenResponse.builder()
            .id(entity.getId()).shortCode(entity.getShortCode())
            .shortUrl(baseUrl + "/" + entity.getShortCode())
            .originalUrl(entity.getOriginalUrl())
            .expireTime(entity.getExpireTime())
            .build();
    }

    private CacheLinkInfo toCacheInfo(ShortLink entity) {
        return entity == null ? null : CacheLinkInfo.builder()
            .shortCode(entity.getShortCode()).originalUrl(entity.getOriginalUrl())
            .expireTime(entity.getExpireTime()).status(entity.getStatus())
            .build();
    }
}