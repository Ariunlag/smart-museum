package com.smartmuseum.heatmap.store;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HeatmapStoreTest {

    @Test
    void enterAndGetCount_shouldIncreaseCount() {
        HeatmapStore store = new HeatmapStore();

        store.enter("A1", 1);
        store.enter("A1", 1);

        assertEquals(2, store.getCount("A1", 1));
    }

    @Test
    void leave_shouldNotGoBelowZero() {
        HeatmapStore store = new HeatmapStore();

        store.enter("B2", 3);
        store.leave("B2", 3);
        store.leave("B2", 3);

        assertEquals(0, store.getCount("B2", 3));
    }

    @Test
    void snapshot_shouldBeUnmodifiable() {
        HeatmapStore store = new HeatmapStore();
        store.enter("C3", 2);

        Map<String, Integer> snapshot = store.snapshot();

        assertThrows(UnsupportedOperationException.class, () -> snapshot.put("2:X1", 99));
    }
}
