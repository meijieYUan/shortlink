package com.shortlink.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shortlink.common.constant.Constants;
import com.shortlink.common.exception.BizException;
import com.shortlink.common.result.ResultCode;
import com.shortlink.common.util.Base62Util;
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
        // 1. Validate URL
        String normalizedUrl = UrlValidator.normalize(request.getOriginalUrl());
        if (!UrlValidator.isValid(normalizedUrl)) {
            throw new BizException(ResultCode.URL_INVALID);
        }

        // 2. Idempotency: check if same URL already exists
        ShortLink existing = shortLinkMapper.selectOne(
            new LambdaQueryWrapper<ShortLink>()
                .eq(ShortLink::getOriginalUrl, normalizedUrl)
                .eq(ShortLink::getStatus, 1)
                .and(w -> w.isNull(ShortLink::getExpireTime)
                           .or().gt(ShortLink::getExpireTime, LocalDateTime.now()))
                .orderByDesc(ShortLink::getId)
                .last("LIMIT 1")
        );
        if (existing != null) {
            log.info("Idempotent hit: existing shortCode={} for URL={}", existing.getShortCode(), normalizedUrl);
            return buildResponse(existing);
        }

        // 3. Insert new record
        LocalDateTime expireTime = request.getExpireTime();
        if (expireTime == null) {
            expireTime = LocalDateTime.now().plusDays(Constants.DEFAULT_EXPIRE_DAYS);
        }

        ShortLink entity = ShortLink.builder()
            .shortCode("") // placeholder, updated after insert
            .originalUrl(normalizedUrl)
            .expireTime(expireTime)
            .status(1)
            .creator("")
            .build();

        int rows = shortLinkMapper.insert(entity);
        if (rows <= 0) {
            throw new BizException(ResultCode.GENERATE_FAILED);
        }

        // 4. Generate short code from auto-increment ID
        String shortCode = Base62Util.encode(entity.getId());
        entity.setShortCode(shortCode);
        shortLinkMapper.updateById(entity);

        log.info("Short link created: id={}, shortCode={}, url={}", entity.getId(), shortCode, normalizedUrl);
        return buildResponse(entity);
    }

    @Override
    public String getOriginalUrl(String shortCode) {
        long id = Base62Util.decode(shortCode);
        ShortLink entity = shortLinkMapper.selectById(id);
        if (entity == null  || entity.getStatus() == 0) {
            return null;
        }
        // Check expiration
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
                return false; // not expired, just not found
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