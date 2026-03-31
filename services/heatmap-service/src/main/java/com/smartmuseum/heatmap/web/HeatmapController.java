package com.smartmuseum.heatmap.web;

import com.smartmuseum.heatmap.store.GridCount;
import com.smartmuseum.heatmap.store.GridCountRepository;
import com.smartmuseum.heatmap.store.HeatmapStore;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin dashboard-д зориулсан REST API.
 *
 * GET /api/heatmap/{floorId}        → Тухайн давхарын real-time хүний тоо
 * GET /api/heatmap/{floorId}/history → MongoDB-д хадгалагдсан түүх
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

    /** Real-time: memory-с уншина */
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

    /** History: MongoDB-с уншина */
    @GetMapping("/{floorId}/history")
    public List<GridCount> getHistory(@PathVariable int floorId) {
        return repository.findByFloorId(floorId);
    }
}