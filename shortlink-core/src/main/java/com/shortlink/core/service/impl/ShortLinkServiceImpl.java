package com.shortlink.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

        // Idempotency check via MD5 hash
        String urlHash = HashUtil.md5Hex(normalizedUrl);
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

        // Generate ID via segment-based generator (Phase 4)
        long id = idGenerator.nextId();
        String shortCode = Base62Util.encode(id);

        LocalDateTime expireTime = request.getExpireTime();
        if (expireTime == null) {
            expireTime = LocalDateTime.now().plusDays(Constants.DEFAULT_EXPIRE_DAYS);
        }

        // Single INSERT: all fields known upfront (no more INSERT + UPDATE)
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

        log.info("Short link created: id={}, shortCode={}", id, shortCode);
        return buildResponse(entity);
    }

    @Override
    public String getOriginalUrl(String shortCode) {
        Optional<CacheLinkInfo> result = cacheService.get(shortCode, () -> {
            long id = Base62Util.decode(shortCode);
            ShortLink entity = shortLinkMapper.selectById(id);
            if (entity == null || entity.getStatus() == 0) return Optional.empty();
            if (entity.getExpireTime() != null && entity.getExpireTime().isBefore(LocalDateTime.now()))
                return Optional.empty();
            return Optional.of(toCacheInfo(entity));
        });
        return result.map(CacheLinkInfo::getOriginalUrl).orElse(null);
    }

    @Override
    public boolean isExpired(String shortCode) {
        Optional<CacheLinkInfo> result = cacheService.get(shortCode, () -> {
            try {
                long id = Base62Util.decode(shortCode);
                ShortLink entity = shortLinkMapper.selectById(id);
                if (entity == null || entity.getStatus() == 0) return Optional.empty();
                return Optional.of(toCacheInfo(entity));
            } catch (Exception e) {
                return Optional.empty();
            }
        });
        /*
        Optional.map() 具有“短路”特性：
        如果result 是 Optional.empty()（空容器），.map() 会直接跳过内部的 Lambda 表达式
        */
        return result.map(info -> info.getExpireTime() != null
            && info.getExpireTime().isBefore(LocalDateTime.now())).orElse(false);
    }

    private ShortenResponse buildResponse(ShortLink entity) {
        return ShortenResponse.builder()
            .id(entity.getId()).shortCode(entity.getShortCode())
            .shortUrl(baseUrl + "/" + entity.getShortCode())
            .originalUrl(entity.getOriginalUrl())
            .expireTime(entity.getExpireTime())
            .build();
    }

    private CacheLinkInfo toCacheInfo(ShortLink entity) {
        return CacheLinkInfo.builder()
            .shortCode(entity.getShortCode()).originalUrl(entity.getOriginalUrl())
            .expireTime(entity.getExpireTime()).status(entity.getStatus())
            .build();
    }
}