package com.smartmuseum.heatmap.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class HeatmapEventGuard {

    private static final long EVENT_TTL_MS   = 10 * 60 * 1000L;
    private static final int  PURGE_INTERVAL = 1000;

    private final Map<String, Long> seenEvents             = new ConcurrentHashMap<>();
    private final Map<String, Long> latestSequenceByDevice = new ConcurrentHashMap<>();
    private final Map<String, Long> deviceLastSeenAt       = new ConcurrentHashMap<>();
    private final AtomicLong callCounter = new AtomicLong();

    /**
     * Returns true if this event should be processed.
     * Rejects duplicates (by eventId) and out-of-order events (by per-device sequence number).
     *
     * Thread-safe: the sequence check uses compute() to eliminate the TOCTOU race that a
     * separate get()+put() pair would expose.
     */
    public boolean shouldProcess(String eventId, String deviceId, Long sequenceNum) {
        long now = System.currentTimeMillis();
        purgeExpiredEventsIfNeeded(now);

        // Duplicate eventId check (putIfAbsent is atomic)
        if (eventId != null && seenEvents.putIfAbsent(eventId, now) != null) {
            return false;
        }

        if (deviceId != null && sequenceNum != null) {
            final boolean[] accepted = {true};
            // compute() makes the read-then-write atomic — no TOCTOU window
            latestSequenceByDevice.compute(deviceId, (id, lastSeen) -> {
                if (lastSeen != null && sequenceNum <= lastSeen) {
                    accepted[0] = false;
                    return lastSeen; // keep the higher value
                }
                return sequenceNum;
            });
            if (!accepted[0]) {
                if (eventId != null) seenEvents.remove(eventId);
                return false;
            }
            deviceLastSeenAt.put(deviceId, now);
        }

        return true;
    }

    private void purgeExpiredEventsIfNeeded(long now) {
        if (callCounter.incrementAndGet() % PURGE_INTERVAL != 0) {
            return;
        }
        seenEvents.entrySet().removeIf(e -> now - e.getValue() > EVENT_TTL_MS);
        // Remove stale device sequence entries to prevent unbounded growth
        deviceLastSeenAt.entrySet().removeIf(e -> {
            if (now - e.getValue() > EVENT_TTL_MS) {
                latestSequenceByDevice.remove(e.getKey());
                return true;
            }
            return false;
        });
    }
}
