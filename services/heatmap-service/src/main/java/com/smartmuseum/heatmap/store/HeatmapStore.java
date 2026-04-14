package com.smartmuseum.heatmap.store;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores people counts per grid in memory.
 * key: "floorId:gridId" -> count
 * Snapshot is persisted to MongoDB by a scheduler.
 */
@Component
public class HeatmapStore {

    private final Map<String, Integer> counts = new ConcurrentHashMap<>();

    private String key(String gridId, int floorId) {
        return floorId + ":" + gridId;
    }

    /** User enters a new grid cell -> +1. */
    public void enter(String gridId, int floorId) {
        counts.merge(key(gridId, floorId), 1, Integer::sum);
    }

    /** User leaves a grid cell -> -1 (never below 0). */
    public void leave(String gridId, int floorId) {
        counts.computeIfPresent(key(gridId, floorId),
                (k, v) -> Math.max(0, v - 1));
    }

    /** Returns current people count for a grid cell. */
    public int getCount(String gridId, int floorId) {
        return counts.getOrDefault(key(gridId, floorId), 0);
    }

    /** Returns a snapshot of all grid counts (used for persistence). */
    public Map<String, Integer> snapshot() {
        return Collections.unmodifiableMap(counts);
    }
}