package com.shortlink.analytics.disruptor;

import com.lmax.disruptor.EventHandler;
import com.shortlink.analytics.event.AccessEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Disruptor EventHandler: batches AccessEvents and publishes to RocketMQ.
 *
 * Batch policy: flush when 500 events accumulate OR 200ms elapsed.
 * This prevents the redirect path from blocking on RocketMQ send.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccessEventConsumer implements EventHandler<AccessEvent> {

    private static final int BATCH_SIZE = 500;
    private static final long FLUSH_INTERVAL_MS = 200;

    private final RocketMQTemplate rocketMQTemplate;

    private final List<AccessEvent> buffer = new ArrayList<>(BATCH_SIZE);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "access-event-flush");
        t.setDaemon(true);
        return t;
    });

    private volatile boolean started;

    @Override
    public void onEvent(AccessEvent event, long sequence, boolean endOfBatch) {
        // Lazy init timer on first event
        if (!started) {
            synchronized (this) {
                if (!started) {
                    scheduler.scheduleAtFixedRate(this::flush, FLUSH_INTERVAL_MS, FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS);
                    started = true;
                }
            }
        }

        synchronized (buffer) {
            buffer.add(event);
            if (buffer.size() >= BATCH_SIZE) {
                flush();
            }
        }
    }

    private void flush() {
        List<AccessEvent> batch;
        synchronized (buffer) {
            if (buffer.isEmpty()) return;
            batch = new ArrayList<>(buffer);
            buffer.clear();
        }

        try {
            // Convert to JSON-friendly format: send as sync for reliability
            rocketMQTemplate.syncSend("shortlink-access-log", batch);
            log.debug("Flushed {} access events to RocketMQ", batch.size());
        } catch (Exception e) {
            log.error("Failed to send access events to RocketMQ, dropping {} events", batch.size(), e);
        }
    }
}