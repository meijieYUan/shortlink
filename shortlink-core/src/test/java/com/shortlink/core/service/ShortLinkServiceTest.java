package com.shortlink.core.service;

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
        @DisplayName("should create a new short link with urlHash")
        void createNew() {
            ShortenRequest req = new ShortenRequest();
            req.setOriginalUrl("https://example.com/path");

            when(shortLinkMapper.selectOne(any())).thenReturn(null);
            when(shortLinkMapper.insert(any(ShortLink.class))).thenAnswer(inv -> {
                ShortLink e = inv.getArgument(0);
                e.setId(100L);
                return 1;
            });
            doReturn(1).when(shortLinkMapper).updateById(any(ShortLink.class));

            ShortenResponse resp = shortLinkService.shorten(req);

            assertThat(resp.getShortCode()).isEqualTo("000001c");
            assertThat(resp.getShortUrl()).isEqualTo("http://short.link/000001c");

            // Verify urlHash was set on the inserted entity
            ArgumentCaptor<ShortLink> insertCaptor = ArgumentCaptor.forClass(ShortLink.class);
            verify(shortLinkMapper).insert(insertCaptor.capture());
            ShortLink inserted = insertCaptor.getValue();
            assertThat(inserted.getUrlHash()).isNotEmpty();
            assertThat(inserted.getUrlHash()).hasSize(16);
        }

        @Test
        @DisplayName("should return existing short link via urlHash (idempotency)")
        void idempotency() {
            ShortenRequest req = new ShortenRequest();
            req.setOriginalUrl("https://example.com");

            // UrlValidator.normalize("https://example.com") -> "https://example.com/"
            ShortLink existing = ShortLink.builder()
                .id(42L).shortCode("00000G0").originalUrl("https://example.com/")
                .urlHash("abc123def4567890")
                .expireTime(LocalDateTime.now().plusDays(30)).status(1).build();
            when(shortLinkMapper.selectOne(any())).thenReturn(existing);

            ShortenResponse resp = shortLinkService.shorten(req);

            assertThat(resp.getShortCode()).isEqualTo("00000G0");
            assertThat(resp.getId()).isEqualTo(42L);
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
    @DisplayName("GetOriginalUrl")
    class GetOriginalUrl {

        @Test
        @DisplayName("should return URL for valid short code")
        void validCode() {
            ShortLink entity = ShortLink.builder()
                .id(1L).shortCode("0000001").originalUrl("https://example.com")
                .expireTime(LocalDateTime.now().plusDays(90)).status(1).build();
            when(shortLinkMapper.selectById(eq(1L))).thenReturn(entity);

            String url = shortLinkService.getOriginalUrl("0000001");
            assertThat(url).isEqualTo("https://example.com");
        }

        @Test
        @DisplayName("should return null for expired link")
        void expiredLink() {
            ShortLink entity = ShortLink.builder()
                .id(1L).shortCode("0000001").originalUrl("https://example.com")
                .expireTime(LocalDateTime.now().minusDays(1)).status(1).build();
            when(shortLinkMapper.selectById(eq(1L))).thenReturn(entity);

            String url = shortLinkService.getOriginalUrl("0000001");
            assertThat(url).isNull();
        }

        @Test
        @DisplayName("should return null for disabled link")
        void disabledLink() {
            ShortLink entity = ShortLink.builder()
                .id(1L).shortCode("0000001").originalUrl("https://example.com")
                .expireTime(LocalDateTime.now().plusDays(90)).status(0).build();
            when(shortLinkMapper.selectById(eq(1L))).thenReturn(entity);

            String url = shortLinkService.getOriginalUrl("0000001");
            assertThat(url).isNull();
        }

        @Test
        @DisplayName("should return null for not-found id")
        void notFound() {
            when(shortLinkMapper.selectById(eq(999L))).thenReturn(null);
            String url = shortLinkService.getOriginalUrl("00000G7");
            assertThat(url).isNull();
        }
    }

    @Nested
    @DisplayName("IsExpired")
    class IsExpired {

        @Test
        @DisplayName("should return true for expired link")
        void expired() {
            ShortLink entity = ShortLink.builder()
                .id(1L).expireTime(LocalDateTime.now().minusHours(1)).status(1).build();
            when(shortLinkMapper.selectById(eq(1L))).thenReturn(entity);

            assertThat(shortLinkService.isExpired("0000001")).isTrue();
        }

        @Test
        @DisplayName("should return false for active link")
        void active() {
            ShortLink entity = ShortLink.builder()
                .id(1L).expireTime(LocalDateTime.now().plusDays(30)).status(1).build();
            when(shortLinkMapper.selectById(eq(1L))).thenReturn(entity);

            assertThat(shortLinkService.isExpired("0000001")).isFalse();
        }
    }
}