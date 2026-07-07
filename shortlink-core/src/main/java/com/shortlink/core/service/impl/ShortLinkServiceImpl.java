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

        // Idempotency check via hash index
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
            log.warn("MD5 hash collision: urlHash={}, existingId={}", urlHash, candidate.getId());
        }

        // Create new record
        LocalDateTime expireTime = request.getExpireTime();
        if (expireTime == null) {
            expireTime = LocalDateTime.now().plusDays(Constants.DEFAULT_EXPIRE_DAYS);
        }

        ShortLink entity = ShortLink.builder()
            .shortCode("")
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

        String shortCode = Base62Util.encode(entity.getId());
        entity.setShortCode(shortCode);
        shortLinkMapper.updateById(entity);

        // Populate cache eagerly
        cacheService.onCreated(shortCode, toCacheInfo(entity));

        log.info("Short link created: id={}, shortCode={}, urlHash={}", entity.getId(), shortCode, urlHash);
        return buildResponse(entity);
    }

    @Override
    public String getOriginalUrl(String shortCode) {
        // Use multi-level cache with DB fallback
        Optional<CacheLinkInfo> result = cacheService.get(shortCode, () -> {
            long id = Base62Util.decode(shortCode);
            ShortLink entity = shortLinkMapper.selectById(id);
            if (entity == null || entity.getStatus() == 0) {
                return Optional.empty();
            }
            if (entity.getExpireTime() != null && entity.getExpireTime().isBefore(LocalDateTime.now())) {
                return Optional.empty();
            }
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
                if (entity == null || entity.getStatus() == 0) {
                    return Optional.empty();
                }
                return Optional.of(toCacheInfo(entity));
            } catch (Exception e) {
                return Optional.empty();
            }
        });

        return result.map(info -> info.getExpireTime() != null
            && info.getExpireTime().isBefore(LocalDateTime.now()))
            .orElse(false);
    }

    private ShortenResponse buildResponse(ShortLink entity) {
        return ShortenResponse.builder()
            .id(entity.getId())
            .shortCode(entity.getShortCode())
            .shortUrl(baseUrl + "/" + entity.getShortCode())
            .originalUrl(entity.getOriginalUrl())
            .expireTime(entity.getExpireTime())
            .build();
    }

    private CacheLinkInfo toCacheInfo(ShortLink entity) {
        return CacheLinkInfo.builder()
            .shortCode(entity.getShortCode())
            .originalUrl(entity.getOriginalUrl())
            .expireTime(entity.getExpireTime())
            .status(entity.getStatus())
            .build();
    }
}