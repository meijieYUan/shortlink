package com.shortlink.analytics.disruptor;

import com.lmax.disruptor.RingBuffer;
import com.shortlink.analytics.event.AccessEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccessEventProducer {

    private final RingBuffer<AccessEvent> ringBuffer;

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