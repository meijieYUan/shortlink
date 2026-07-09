package com.shortlink.core.idgen;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Segment-mode distributed ID generator with double-buffer async prefetch.
 *
 * Design:
 * - Fast path: lock-free ID consumption via AtomicLong on current segment
 * - Slow path: synchronized segment swap when current exhausted
 * - Prefetch: async background thread pre-loads next segment before current runs out
 *
 * DB allocation: UPDATE t_id_segment SET max_id = max_id + step WHERE biz_tag = ?
 * This UPDATE is atomic across connections, ensuring globally unique ID ranges
 * even with multiple application instances.
 *
 * Expected throughput: 500k+ TPS single-machine.
 */
@Slf4j
@Component
public class SegmentIdGenerator implements IdGenerator, InitializingBean {

    private static final String BIZ_TAG = "short_link";

    private final SegmentIdMapper mapper;

    // Double buffer: current + prefetched next
    private volatile Segment current;
    private volatile Segment next;
    private volatile boolean prefetching;

    // Single-thread executor for async segment prefetch
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "idgen-prefetch");
        t.setDaemon(true);
        return t;
    });

    public SegmentIdGenerator(SegmentIdMapper mapper) {
        this.mapper = mapper;
    }

    // ---- Public API ----

    /**
     * Next globally unique ID.
     *
     * Fast path (no lock): AtomicLong.getAndIncrement on current segment
     * Slow path (synchronized): swap to next segment or load from DB
     */
    @Override
    public long nextId() {
        // Fast path — lock-free, high throughput
        Segment seg = current;
        if (seg != null) {
            long id = seg.tryNextId();
            if (id > 0) return id;
        }

        // Slow path — synchronized segment management
        synchronized (this) {
            // Double-check: another thread might have swapped while we waited（其他线程可能已经更新了片段）
            seg = current;
            if (seg != null) {
                long id = seg.tryNextId();
                if (id > 0) return id;
            }

            // Try to use prefetched next segment
            if (next != null) {
                current = next;
                next = null;
                triggerPrefetch();
                return assertNextId();
            }

            // First load or next not ready yet — load from DB synchronously
            current = loadSegmentFromDb();
            triggerPrefetch();
            return assertNextId();
        }
    }

    // ---- Lifecycle ----

    @Override
    public void afterPropertiesSet() {
        log.info("Initializing SegmentIdGenerator...");
        current = loadSegmentFromDb();
        triggerPrefetch();
        log.info("SegmentIdGenerator ready: current=[{}, {}]", current.start, current.end);
    }

    // ---- Internal ----

    /**
     * Load a new segment from DB. Allocates a range via atomic UPDATE then reads back.
     */
    private Segment loadSegmentFromDb() {
        // Read current state
        SegmentIdEntity entity = mapper.selectOne(
            new LambdaQueryWrapper<SegmentIdEntity>().eq(SegmentIdEntity::getBizTag, BIZ_TAG));

        if (entity == null) {
            throw new IllegalStateException("t_id_segment not initialized for biz_tag=" + BIZ_TAG);
        }

        long oldMaxId = entity.getMaxId();
        int step = entity.getStep();

        // Atomically allocate next range: UPDATE ... SET max_id = max_id + step
        int rows = mapper.allocateSegment(BIZ_TAG);
        if (rows == 0) {
            throw new IllegalStateException("Segment allocation failed for biz_tag=" + BIZ_TAG);
        }

        long newStart = oldMaxId + 1;
        long newEnd = oldMaxId + step;
        log.info("Segment allocated: [{}, {}], step={}", newStart, newEnd, step);
        return new Segment(newStart, newEnd);
    }

    /**
     * Trigger async prefetch of next segment if not already in progress.
     */
    private void triggerPrefetch() {
        if (next == null && !prefetching) {
            prefetching = true;
            executor.submit(() -> {
                try {
                    next = loadSegmentFromDb();
                    log.debug("Segment prefetched: [{}, {}]", next.start, next.end);
                } catch (Exception e) {
                    log.error("Segment prefetch failed", e);
                } finally {
                    prefetching = false;
                }
            });
        }
    }

    /**
     * Assert that current segment exists and has remaining IDs, then return one.
     */
    private long assertNextId() {
        long id = current.tryNextId();
        if (id <= 0) {
            throw new IllegalStateException("Segment exhaustion: no ID available after swap");
        }
        return id;
    }

    // ---- Segment holder ----

    /**
     * Immutable segment representing a contiguous range of allocated IDs.
     * Thread-safe consumption via AtomicLong.
     */
    private static class Segment {
        final long start;
        final long end;
        final AtomicLong pos;

        Segment(long start, long end) {
            this.start = start;
            this.end = end;
            this.pos = new AtomicLong(start);
        }

        /**
         * Try to consume next ID from this segment.
         * @return the ID, or -1 if segment is exhausted
         */
        long tryNextId() {
            long id = pos.getAndIncrement();
            return id <= end ? id : -1;
        }
    }
}