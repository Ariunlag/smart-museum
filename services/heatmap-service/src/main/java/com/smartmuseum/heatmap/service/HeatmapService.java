package com.smartmuseum.heatmap.service;

import com.smartmuseum.heatmap.config.HeatmapProperties;
import com.smartmuseum.heatmap.store.GridCount;
import com.smartmuseum.heatmap.store.GridCountRepository;
import com.smartmuseum.heatmap.store.HeatmapStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class HeatmapService {

    private static final Logger log = LoggerFactory.getLogger(HeatmapService.class);

    private final HeatmapStore        store;
    private final GridCountRepository repository;
    private final HeatmapProperties   props;

    public HeatmapService(HeatmapStore store,
                          GridCountRepository repository,
                          HeatmapProperties props) {
        this.store      = store;
        this.repository = repository;
        this.props      = props;
    }

    /**
     * Called when an MQTT event arrives.
     * gridId -> +1 (new location)
     * prevGridId -> -1 (previous location, null on first entry)
     */
    public void processEvent(String gridId, int floorId, String prevGridId, Integer prevFloorId) {
        // Өмнөх grid-с гарсан
        if (prevGridId != null) {
            int fromFloor = prevFloorId != null ? prevFloorId : floorId;
            store.leave(prevGridId, fromFloor);
        }
        // Шинэ grid-д орсон
        store.enter(gridId, floorId);

        // Crowd threshold шалгана
        int count = store.getCount(gridId, floorId);
        if (count >= props.getHeatmap().getCrowdThreshold()) {
            log.warn("CROWD ALERT: grid={} floor={} count={}", gridId, floorId, count);
            // TODO: MQTT-р alert publish хийнэ (дараа нэмнэ)
        }
    }

    /** Handles user disconnect events. */
    public void processLeave(String gridId, int floorId) {
        if (gridId != null) {
            store.leave(gridId, floorId);
        }
    }

    /**
     * Persists heatmap snapshot to MongoDB on the configured schedule.
     * fixedDelayString is loaded from YAML configuration.
     */
    @Scheduled(fixedDelayString = "#{${museum.heatmap.persist-interval-minutes} * 60000}")
    public void persist() {
        Map<String, Integer> snapshot = store.snapshot();
        snapshot.forEach((key, count) -> {
            // key format: "floorId:gridId"
            String[] parts   = key.split(":");
            int    floorId   = Integer.parseInt(parts[0]);
            String gridId    = parts[1];

            var existing = repository.findByGridIdAndFloorId(gridId, floorId);
            if (count <= 0) {
                existing.ifPresent(repository::delete);
                return;
            }
            GridCount doc = existing.orElse(new GridCount(gridId, floorId));
            doc.setCount(count);
            doc.setUpdatedAt(System.currentTimeMillis());
            repository.save(doc);
        });
        log.info("Heatmap persisted: {} grids", snapshot.size());
    }
}