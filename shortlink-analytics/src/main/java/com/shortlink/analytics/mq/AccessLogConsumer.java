package com.shortlink.analytics.mq;

import com.shortlink.analytics.event.AccessEvent;
import com.shortlink.analytics.model.AccessLog;
import com.shortlink.analytics.service.AccessLogService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RocketMQMessageListener(
    topic = "shortlink-access-log",
    consumerGroup = "shortlink-access-consumer",
    maxReconsumeTimes = 3
)
public class AccessLogConsumer implements RocketMQListener<List<AccessEvent>> {

    private final AccessLogService accessLogService;

    public AccessLogConsumer(AccessLogService accessLogService) {
        this.accessLogService = accessLogService;
    }

    @Override
    public void onMessage(List<AccessEvent> batch) {
        if (batch == null || batch.isEmpty()) return;
        try {
            List<AccessLog> logs = batch.stream().map(e -> AccessLog.builder()
                .shortCode(e.getShortCode())
                .ip(e.getIp() != null ? e.getIp() : "")
                .userAgent(e.getUserAgent() != null ? e.getUserAgent() : "")
                .referer(e.getReferer() != null ? e.getReferer() : "")
                .accessTime(e.getTimestamp())
                .build()).collect(Collectors.toList());
            accessLogService.batchInsert(logs);
            log.debug("Persisted {} access events", logs.size());
        } catch (Exception e) {
            log.error("Error persisting access events batch", e);
            throw e;
        }
    }
}