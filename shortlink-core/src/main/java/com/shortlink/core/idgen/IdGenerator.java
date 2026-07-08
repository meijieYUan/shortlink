package com.shortlink.core.idgen;

/**
 * Abstract ID generator interface.
 * Implementations: SegmentIdGenerator (Phase 4), SnowflakeIdGenerator (future).
 */
public interface IdGenerator {

    /**
     * Generate the next globally unique ID.
     */
    long nextId();
}