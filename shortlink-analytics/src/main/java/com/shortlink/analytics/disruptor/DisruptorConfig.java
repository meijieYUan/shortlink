package com.shortlink.analytics.disruptor;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.shortlink.analytics.event.AccessEvent;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ThreadFactory;

@Configuration
public class DisruptorConfig {

    private static final int RING_BUFFER_SIZE = 16384;

    @Value("${shortlink.rocketmq.topic:shortlink-access-log}")
    private String topic;

    @Bean
    public AccessEventConsumer accessEventConsumer(RocketMQTemplate rocketMQTemplate) {
        return new AccessEventConsumer(rocketMQTemplate, topic);
    }

    @Bean
    public RingBuffer<AccessEvent> accessEventRingBuffer(AccessEventConsumer consumer) {
        ThreadFactory threadFactory = r -> {
            Thread t = new Thread(r, "access-event-disruptor");
            t.setDaemon(false);
            return t;
        };

        Disruptor<AccessEvent> disruptor = new Disruptor<>(
            AccessEvent::new,
            RING_BUFFER_SIZE,
            threadFactory,
            ProducerType.MULTI,
            new BlockingWaitStrategy()
        );

        disruptor.handleEventsWith(consumer);
        disruptor.start();
        return disruptor.getRingBuffer();
    }
}