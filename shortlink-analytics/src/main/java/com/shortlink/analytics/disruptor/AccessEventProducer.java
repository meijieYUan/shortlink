package com.shortlink.analytics.disruptor;

import com.lmax.disruptor.RingBuffer;
import com.shortlink.analytics.event.AccessEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Publishes access events to the Disruptor RingBuffer.
 * Non-blocking publish: tryPublishEvent returns immediately if buffer is full.
 * Called（调用） from ShortLinkController on successful redirect.
 */
@Component
@RequiredArgsConstructor
public class AccessEventProducer {

    private final RingBuffer<AccessEvent> ringBuffer;

    /**
     * Publish an access event. Non-blocking — silently drops if ring buffer is full.
     */
    public void publish(String shortCode, String ip, String userAgent, String referer) {
        ringBuffer.publishEvent((event, sequence) -> {
            event.setShortCode(shortCode);
            event.setIp(ip);
            event.setUserAgent(userAgent);
            event.setReferer(referer);
            event.setTimestamp(java.time.LocalDateTime.now());
        });
    }
}