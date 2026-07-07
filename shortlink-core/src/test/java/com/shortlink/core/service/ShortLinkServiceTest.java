package com.shortlink.core.service;

import com.shortlink.cache.model.CacheLinkInfo;
import com.shortlink.cache.strategy.ShortLinkCacheService;
import com.shortlink.core.model.dto.ShortenRequest;
import com.shortlink.core.model.dto.ShortenResponse;
import com.shortlink.core.model.entity.ShortLink;
import com.shortlink.core.repository.ShortLinkMapper;
import com.shortlink.core.service.impl.ShortLinkServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShortLinkServiceTest {

    @Mock
    private ShortLinkMapper shortLinkMapper;
    @Mock
    private ShortLinkCacheService cacheService;

    @InjectMocks
    private ShortLinkServiceImpl shortLinkService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(shortLinkService, "baseUrl", "http://short.link");
    }

    @Nested
    @DisplayName("Shorten")
    class Shorten {

        @Test
        @DisplayName("should create a new short link and populate cache")
        void createNew() {
            ShortenRequest req = new ShortenRequest();
            req.setOriginalUrl("https://example.com/path");

            when(shortLinkMapper.selectList(any())).thenReturn(Collections.emptyList());
            when(shortLinkMapper.insert(any(ShortLink.class))).thenAnswer(inv -> {
                ShortLink e = inv.getArgument(0);
                e.setId(100L);
                return 1;
            });
            doReturn(1).when(shortLinkMapper).updateById(any(ShortLink.class));

            ShortenResponse resp = shortLinkService.shorten(req);

            assertThat(resp.getShortCode()).isEqualTo("000001c");

            // Verify cache was populated
            verify(cacheService).onCreated(eq("000001c"), any(CacheLinkInfo.class));
        }

        @Test
        @DisplayName("should return existing via hash match (idempotency)")
        void idempotency() {
            ShortenRequest req = new ShortenRequest();
            req.setOriginalUrl("https://example.com");

            ShortLink existing = ShortLink.builder()
                .id(42L).shortCode("00000G0").originalUrl("https://example.com/")
                .urlHash("d41d8cd98f00b204e9800998ecf8427e")
                .expireTime(LocalDateTime.now().plusDays(30)).status(1).build();

            when(shortLinkMapper.selectList(any())).thenReturn(List.of(existing));

            ShortenResponse resp = shortLinkService.shorten(req);

            assertThat(resp.getShortCode()).isEqualTo("00000G0");
        }

        @Test
        @DisplayName("should reject invalid URL")
        void rejectInvalidUrl() {
            ShortenRequest req = new ShortenRequest();
            req.setOriginalUrl("not-a-url");

            assertThatThrownBy(() -> shortLinkService.shorten(req))
                .isInstanceOf(com.shortlink.common.exception.BizException.class);
        }
    }

    @Nested
    @DisplayName("GetOriginalUrl (via cache)")
    class GetOriginalUrl {

        @Test
        @DisplayName("should return URL from cache")
        void fromCache() {
            CacheLinkInfo info = CacheLinkInfo.builder()
                .shortCode("0000001").originalUrl("https://example.com")
                .expireTime(LocalDateTime.now().plusDays(90)).status(1).build();

            when(cacheService.get(eq("0000001"), any())).thenReturn(Optional.of(info));

            String url = shortLinkService.getOriginalUrl("0000001");
            assertThat(url).isEqualTo("https://example.com");
        }

        @Test
        @DisplayName("should return null when cache returns empty")
        void cacheEmpty() {
            when(cacheService.get(eq("0000001"), any())).thenReturn(Optional.empty());

            String url = shortLinkService.getOriginalUrl("0000001");
            assertThat(url).isNull();
        }
    }

    @Nested
    @DisplayName("IsExpired (via cache)")
    class IsExpired {

        @Test
        @DisplayName("should return true when cache info is expired")
        void expired() {
            CacheLinkInfo info = CacheLinkInfo.builder()
                .shortCode("0000001").originalUrl("https://example.com")
                .expireTime(LocalDateTime.now().minusHours(1)).status(1).build();

            when(cacheService.get(eq("0000001"), any())).thenReturn(Optional.of(info));

            assertThat(shortLinkService.isExpired("0000001")).isTrue();
        }

        @Test
        @DisplayName("should return false when cache info is active")
        void active() {
            CacheLinkInfo info = CacheLinkInfo.builder()
                .shortCode("0000001").originalUrl("https://example.com")
                .expireTime(LocalDateTime.now().plusDays(30)).status(1).build();

            when(cacheService.get(eq("0000001"), any())).thenReturn(Optional.of(info));

            assertThat(shortLinkService.isExpired("0000001")).isFalse();
        }
    }
}