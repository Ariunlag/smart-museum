package com.smartmuseum.heatmap.web;

import com.smartmuseum.heatmap.store.GridCount;
import com.smartmuseum.heatmap.store.GridCountRepository;
import com.smartmuseum.heatmap.store.HeatmapStore;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API used by the admin dashboard.
 *
 * GET /api/heatmap/{floorId}         -> real-time people count for a floor
 * GET /api/heatmap/{floorId}/history -> persisted history from MongoDB
 */
import org.springframework.web.bind.annotation.CrossOrigin;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/heatmap")
public class HeatmapController {

    private final HeatmapStore        store;
    private final GridCountRepository repository;

    public HeatmapController(HeatmapStore store, GridCountRepository repository) {
        this.store      = store;
        this.repository = repository;
    }

    /** Reads real-time floor data from in-memory store. */
    @GetMapping("/{floorId}")
    public Map<String, Object> getFloorHeatmap(@PathVariable int floorId) {
        var snapshot = store.snapshot();

        // Тухайн давхарын grid-үүдийг шүүнэ
        var floorData = snapshot.entrySet().stream()
                .filter(e -> e.getKey().startsWith(floorId + ":"))
                .collect(java.util.stream.Collectors.toMap(
                        e -> e.getKey().split(":")[1], // gridId
                        Map.Entry::getValue             // count
                ));

        return Map.of(
                "floorId", floorId,
                "grids",   floorData,
                "total",   floorData.values().stream().mapToInt(Integer::intValue).sum()
        );
    }

    /** Reads historical floor data from MongoDB. */
    @GetMapping("/{floorId}/history")
    public List<GridCount> getHistory(@PathVariable int floorId) {
        return repository.findByFloorId(floorId);
    }
}