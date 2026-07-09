package com.shortlink.analytics.disruptor;

import com.lmax.disruptor.EventHandler;
import com.shortlink.analytics.event.AccessEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Disruptor EventHandler: batches AccessEvents and publishes to RocketMQ asynchronously.
 *
 * Batch policy: flush when 500 events accumulate OR 200ms elapsed.
 * Uses RocketMQ asyncSend to avoid blocking the consumer thread.
 * On send failure: events are re-added to the buffer for retry on next flush cycle.
 */
@Slf4j
public class AccessEventConsumer implements EventHandler<AccessEvent> {

    private static final int BATCH_SIZE = 500;
    private static final long FLUSH_INTERVAL_MS = 200;

    private final RocketMQTemplate rocketMQTemplate;
    private final String topic;

    private final List<AccessEvent> buffer = new ArrayList<>(BATCH_SIZE);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "access-event-flush");
        t.setDaemon(true);
        return t;
    });

    private volatile boolean started;

    public AccessEventConsumer(RocketMQTemplate rocketMQTemplate, String topic) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.topic = topic;
    }

    @Override
    public void onEvent(AccessEvent event, long sequence, boolean endOfBatch) {
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

        if (batch.isEmpty()) return;

        // Async send — non-blocking, does not stall the consumer thread
        rocketMQTemplate.asyncSend(topic, MessageBuilder.withPayload(batch).build(),
            new SendCallback() {
                @Override
                public void onSuccess(SendResult result) {
                    log.debug("Flushed {} access events to RocketMQ, msgId={}", batch.size(), result.getMsgId());
                }

                @Override
                public void onException(Throwable e) {
                    log.error("Failed to send {} access events to RocketMQ, re-enqueuing for retry", batch.size(), e);
                    // Re-add failed batch to the buffer for retry
                    synchronized (buffer) {
                        if (buffer.size() + batch.size() <= BATCH_SIZE * 2) {
                            buffer.addAll(0, batch); // prepend to retry sooner
                        } else {
                            log.warn("Buffer overflow, dropping {} events from failed batch", batch.size());
                        }
                    }
                }
            });
    }
}