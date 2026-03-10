package com.smartmuseum.heatmap.store;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Grid бүрийн хүний тоог memory-д хадгална.
 * key: "floorId:gridId" → count
 * Scheduler тутам MongoDB-д persist хийнэ.
 */
@Component
public class HeatmapStore {

    private final Map<String, Integer> counts = new ConcurrentHashMap<>();

    private String key(String gridId, int floorId) {
        return floorId + ":" + gridId;
    }

    /** Хүн шинэ grid-д орлоо → +1 */
    public void enter(String gridId, int floorId) {
        counts.merge(key(gridId, floorId), 1, Integer::sum);
    }

    /** Хүн grid-с гарлаа → -1 (0-с доош бууруулахгүй) */
    public void leave(String gridId, int floorId) {
        counts.computeIfPresent(key(gridId, floorId),
                (k, v) -> Math.max(0, v - 1));
    }

    /** Grid-н одоогийн хүний тоо */
    public int getCount(String gridId, int floorId) {
        return counts.getOrDefault(key(gridId, floorId), 0);
    }

    /** Бүх grid-н snapshot (persist-д ашиглана) */
    public Map<String, Integer> snapshot() {
        return Collections.unmodifiableMap(counts);
    }
}