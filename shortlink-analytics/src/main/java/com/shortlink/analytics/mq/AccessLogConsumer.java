package com.shortlink.analytics.mq;

import com.shortlink.analytics.event.AccessEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * RocketMQ consumer for access log events.
 * Receives batched AccessEvents and logs them for Phase 5 MVP.
 * Phase 5b will persist to Elasticsearch + MySQL aggregation.
 */
@Slf4j
@Component
@RocketMQMessageListener(
    topic = "shortlink-access-log",
    consumerGroup = "shortlink-access-consumer",
    maxReconsumeTimes = 3
)
public class AccessLogConsumer implements RocketMQListener<List<AccessEvent>> {

    @Override
    public void onMessage(List<AccessEvent> batch) {
        if (batch == null || batch.isEmpty()) {
            return;
        }
        try {
            log.info("Received {} access events. Phase 5b will persist to ES/MySQL.", batch.size());
            // Log first few events for debugging
            batch.stream().limit(3).forEach(e ->
                log.debug("Access: shortCode={}, ip={}, referer={}, ts={}",
                    e.getShortCode(), e.getIp(), e.getReferer(), e.getTimestamp())
            );
        } catch (Exception e) {
            log.error("Error processing access events batch", e);
            throw e; // trigger RocketMQ retry
        }
    }
}