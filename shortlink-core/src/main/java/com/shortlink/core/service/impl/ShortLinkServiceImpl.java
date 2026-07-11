package com.shortlink.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shortlink.cache.model.CacheLinkInfo;
import com.shortlink.cache.strategy.ShortLinkCacheService;
import com.shortlink.common.auth.TenantContext;
import com.shortlink.common.constant.Constants;
import com.shortlink.common.exception.BizException;
import com.shortlink.common.result.ResultCode;
import com.shortlink.common.util.Base62Util;
import com.shortlink.common.util.HashUtil;
import com.shortlink.common.util.UrlValidator;
import com.shortlink.core.config.ShortCodeBlacklist;
import com.shortlink.core.idgen.IdGenerator;
import com.shortlink.domain.model.dto.ShortenRequest;
import com.shortlink.domain.model.dto.ShortenResponse;
import com.shortlink.domain.model.entity.ShortLink;
import com.shortlink.core.repository.ShortLinkMapper;
import com.shortlink.domain.service.OpenApiLinkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShortLinkServiceImpl implements OpenApiLinkService {

    private final ShortLinkMapper shortLinkMapper;
    private final ShortLinkCacheService cacheService;
    private final IdGenerator idGenerator;
    private final ShortCodeBlacklist blacklist;

    @Value("${shortlink.base-url:http://localhost:8080}")
    private String baseUrl;

    @Override
    @Transactional
    public ShortenResponse shorten(ShortenRequest request) {
        String normalizedUrl = UrlValidator.normalize(request.getOriginalUrl());
        if (!UrlValidator.isValid(normalizedUrl)) throw new BizException(ResultCode.URL_INVALID);

        String appKey = TenantContext.get();
        String urlHash = HashUtil.md5Hex(normalizedUrl);

        List<ShortLink> candidates = shortLinkMapper.selectList(
            buildTenantQw().eq(ShortLink::getUrlHash, urlHash).eq(ShortLink::getStatus, 1)
                .and(w -> w.isNull(ShortLink::getExpireTime).or().gt(ShortLink::getExpireTime, LocalDateTime.now())));
        for (ShortLink candidate : candidates)
            if (normalizedUrl.equals(candidate.getOriginalUrl())) return buildResponse(candidate);

        String shortCode; Long id = null;
        if (request.getCustomCode() != null && !request.getCustomCode().isBlank()) {
            shortCode = request.getCustomCode().trim();
            if (shortCode.length() < blacklist.getMinCustomLength()) throw new BizException(ResultCode.SHORT_CODE_TOO_SHORT);
            if (blacklist.isBlacklisted(shortCode)) throw new BizException(ResultCode.SHORT_CODE_BLACKLISTED);
            ShortLink existing = shortLinkMapper.selectOne(new LambdaQueryWrapper<ShortLink>().eq(ShortLink::getShortCode, shortCode));
            if (existing != null) throw new BizException(ResultCode.SHORT_CODE_CONFLICT);
        } else { id = idGenerator.nextId(); shortCode = Base62Util.encode(id); }

        LocalDateTime expireTime = request.getExpireTime();
        if (expireTime == null) expireTime = LocalDateTime.now().plusDays(Constants.DEFAULT_EXPIRE_DAYS);

        ShortLink entity = ShortLink.builder().id(id).shortCode(shortCode).originalUrl(normalizedUrl)
            .urlHash(urlHash).appKey(appKey).expireTime(expireTime).status(1).creator("").build();
        shortLinkMapper.insert(entity);
        cacheService.onCreated(shortCode, toCacheInfo(entity));
        return buildResponse(entity);
    }

    @Override
    public String getOriginalUrl(String shortCode) {
        Optional<CacheLinkInfo> r = cacheService.get(shortCode, () ->
            Optional.ofNullable(shortLinkMapper.selectOne(new LambdaQueryWrapper<ShortLink>().eq(ShortLink::getShortCode, shortCode))).map(this::toCacheInfo));
        return r.map(CacheLinkInfo::getOriginalUrl).orElse(null);
    }

    @Override
    public boolean isExpired(String shortCode) {
        Optional<CacheLinkInfo> r = cacheService.get(shortCode, () -> {
            ShortLink e = shortLinkMapper.selectOne(new LambdaQueryWrapper<ShortLink>().eq(ShortLink::getShortCode, shortCode));
            if (e == null || e.getStatus() != 1) return Optional.empty();
            return Optional.of(toCacheInfo(e));
        });
        return r.map(i -> i.getExpireTime() != null && i.getExpireTime().isBefore(LocalDateTime.now())).orElse(false);
    }

    @Override
    public Page<ShortLink> listLinks(int page, int size, Integer status, String keyword) {
        LambdaQueryWrapper<ShortLink> qw = buildTenantQw();
        if (status != null) qw.eq(ShortLink::getStatus, status);
        if (keyword != null && !keyword.isBlank()) qw.and(w -> w.like(ShortLink::getShortCode, keyword).or().like(ShortLink::getOriginalUrl, keyword));
        qw.orderByDesc(ShortLink::getCreateTime);
        return shortLinkMapper.selectPage(new Page<>(page, size), qw);
    }

    @Override
    public ShortLink getLink(String shortCode) {
        return shortLinkMapper.selectOne(buildTenantQw().eq(ShortLink::getShortCode, shortCode));
    }

    @Override
    @Transactional
    public ShortLink updateLink(String shortCode, Integer newStatus, String newCustomCode, LocalDateTime newExpireTime) {
        ShortLink entity = getLink(shortCode);
        if (entity == null) throw new IllegalArgumentException("Short code not found: " + shortCode);
        if (newStatus != null) entity.setStatus(newStatus);
        if (newCustomCode != null && !newCustomCode.isBlank()) {
            ShortLink conflict = shortLinkMapper.selectOne(new LambdaQueryWrapper<ShortLink>().eq(ShortLink::getShortCode, newCustomCode));
            if (conflict != null && !conflict.getId().equals(entity.getId())) throw new BizException(ResultCode.SHORT_CODE_CONFLICT);
            entity.setShortCode(newCustomCode);
        }
        if (newExpireTime != null) entity.setExpireTime(newExpireTime);
        shortLinkMapper.updateById(entity);
        cacheService.onUpdated(shortCode);
        return entity;
    }

    @Override
    @Transactional
    public void softDelete(String shortCode) {
        ShortLink entity = getLink(shortCode);
        if (entity == null) return;
        entity.setStatus(2); shortLinkMapper.updateById(entity);
        cacheService.onUpdated(shortCode);
    }

    @Override
    @Transactional
    public void restore(String shortCode) {
        ShortLink entity = getLink(shortCode);
        if (entity == null || entity.getStatus() != 2) throw new IllegalArgumentException("Link not in recycle bin: " + shortCode);
        entity.setStatus(1); shortLinkMapper.updateById(entity);
        cacheService.onUpdated(shortCode);
    }

    @Transactional
    public int expireLinks() {
        List<ShortLink> expired = shortLinkMapper.selectList(new LambdaQueryWrapper<ShortLink>().eq(ShortLink::getStatus, 1).le(ShortLink::getExpireTime, LocalDateTime.now()));
        for (ShortLink link : expired) { link.setStatus(0); shortLinkMapper.updateById(link); cacheService.onUpdated(link.getShortCode()); }
        if (!expired.isEmpty()) log.info("Expired {} links", expired.size());
        return expired.size();
    }

    @Transactional
    public int cleanRecycleBin(int retentionDays) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        List<ShortLink> expired = shortLinkMapper.selectList(new LambdaQueryWrapper<ShortLink>().eq(ShortLink::getStatus, 2).le(ShortLink::getUpdateTime, cutoff));
        for (ShortLink link : expired) shortLinkMapper.deleteById(link.getId());
        if (!expired.isEmpty()) log.info("Recycle bin cleanup: {} links permanently deleted", expired.size());
        return expired.size();
    }

    @Override
    public java.util.Map<String, Object> getStats() {
        long active = shortLinkMapper.selectCount(buildTenantQw().eq(ShortLink::getStatus, 1));
        long recycled = shortLinkMapper.selectCount(buildTenantQw().eq(ShortLink::getStatus, 2));
        long total = shortLinkMapper.selectCount(buildTenantQw());
        return java.util.Map.of("total", total, "active", active, "recycled", recycled);
    }

    @Override
    public long countTodayLinks() {
        return shortLinkMapper.selectCount(buildTenantQw().ge(ShortLink::getCreateTime, LocalDate.now().atStartOfDay()));
    }

    private LambdaQueryWrapper<ShortLink> buildTenantQw() {
        LambdaQueryWrapper<ShortLink> qw = new LambdaQueryWrapper<>();
        String appKey = TenantContext.get();
        if (!appKey.isEmpty()) qw.eq(ShortLink::getAppKey, appKey);
        return qw;
    }

    private ShortenResponse buildResponse(ShortLink e) {
        return ShortenResponse.builder().id(e.getId()).shortCode(e.getShortCode()).shortUrl(baseUrl + "/" + e.getShortCode()).originalUrl(e.getOriginalUrl()).expireTime(e.getExpireTime()).build();
    }

    private CacheLinkInfo toCacheInfo(ShortLink e) {
        return e == null ? null : CacheLinkInfo.builder().shortCode(e.getShortCode()).originalUrl(e.getOriginalUrl()).appKey(e.getAppKey()).expireTime(e.getExpireTime()).status(e.getStatus()).build();
    }
}