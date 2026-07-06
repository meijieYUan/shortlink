package com.shortlink.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class ShortLinkServiceImpl implements ShortLinkService {

    private final ShortLinkMapper shortLinkMapper;

    @Value("${shortlink.base-url:http://localhost:8080}")
    private String baseUrl;

    @Override
    @Transactional
    public ShortenResponse shorten(ShortenRequest request) {
        // 1. Validate and normalize URL
        String normalizedUrl = UrlValidator.normalize(request.getOriginalUrl());
        if (!UrlValidator.isValid(normalizedUrl)) {
            throw new BizException(ResultCode.URL_INVALID);
        }

        // 2. Compute URL hash for O(1) idempotency dedup
        String urlHash = HashUtil.sha256Hex16(normalizedUrl);

        // 3. Check existing via unique hash index (O(1))
        ShortLink existing = shortLinkMapper.selectOne(
            new LambdaQueryWrapper<ShortLink>()
                .eq(ShortLink::getUrlHash, urlHash)
                .eq(ShortLink::getStatus, 1)
                .and(w -> w.isNull(ShortLink::getExpireTime)
                           .or().gt(ShortLink::getExpireTime, LocalDateTime.now()))
                .last("LIMIT 1")
        );

        // Secondary compare original URL to guard against hash collision (extremely rare)
        if (existing != null && normalizedUrl.equals(existing.getOriginalUrl())) {
            log.info("Idempotent hit: existing shortCode={} for URL={}", existing.getShortCode(), normalizedUrl);
            return buildResponse(existing);
        }

        // 4. Insert new record
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

        // 5. Generate short code from auto-increment ID
        String shortCode = Base62Util.encode(entity.getId());
        entity.setShortCode(shortCode);
        shortLinkMapper.updateById(entity);

        log.info("Short link created: id={}, shortCode={}, urlHash={}", entity.getId(), shortCode, urlHash);
        return buildResponse(entity);
    }

    @Override
    public String getOriginalUrl(String shortCode) {
        long id = Base62Util.decode(shortCode);
        ShortLink entity = shortLinkMapper.selectById(id);
        if (entity == null || entity.getStatus() == 0) {
            return null;
        }
        if (entity.getExpireTime() != null && entity.getExpireTime().isBefore(LocalDateTime.now())) {
            return null;
        }
        return entity.getOriginalUrl();
    }

    @Override
    public boolean isExpired(String shortCode) {
        try {
            long id = Base62Util.decode(shortCode);
            ShortLink entity = shortLinkMapper.selectById(id);
            if (entity == null || entity.getStatus() == 0) {
                return false;
            }
            return entity.getExpireTime() != null && entity.getExpireTime().isBefore(LocalDateTime.now());
        } catch (Exception e) {
            return false;
        }
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
}