package com.smartmuseum.artinfo.service;

import com.smartmuseum.artinfo.config.MuseumProperties;
import com.smartmuseum.artinfo.domain.Art;
import com.smartmuseum.artinfo.repository.ArtRepository;
import com.smartmuseum.artinfo.web.dto.ArtByIdResponse;
import com.smartmuseum.artinfo.web.dto.ArtInfoResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

@Service
public class ArtInfoService {

    private final ArtRepository repository;
    private final MuseumProperties props;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${QDRANT_BASE_URL:http://qdrant:6333}")
    private String qdrantBaseUrl;

    @Value("${QDRANT_COLLECTION:fingerprints}")
    private String qdrantCollection;

    public ArtInfoService(ArtRepository repository, MuseumProperties props) {
        this.repository = repository;
        this.props = props;
    }

    public ArtInfoResponse findNearest(String gridId, int floorId) {
        int[] gridXY = parseGridId(gridId);
        List<ArtInfoResponse.ArtDto> nearestArts = repository.findByFloorId(floorId)
                .stream()
            .filter(a -> manhattanDist(a.getX(), a.getY(), gridXY[0], gridXY[1]) <= props.getArt().getNearbyRange())
            .sorted(Comparator.comparingInt(a -> manhattanDist(a.getX(), a.getY(), gridXY[0], gridXY[1])))
            .limit(props.getArt().getMaxResults())
                .map(a -> new ArtInfoResponse(
                List.of(new ArtInfoResponse.ArtDto(
                    a.getId(),
                    a.getTitle(),
                    a.getArtist(),
                    a.getDescription()
                ))
                ))
            .flatMap(response -> response.arts().stream())
            .toList();
        return new ArtInfoResponse(nearestArts);
    }

    private int[] parseGridId(String gridId) {
        if (gridId == null || gridId.isBlank() || gridId.equalsIgnoreCase("UNKNOWN")) {
            throw new NumberFormatException("gridId is unknown or null: " + gridId);
        }

        String normalized = gridId.trim().toUpperCase();

        // Expected format from positioning-service: "B3" -> x=2, y=1
        if (normalized.matches("^[A-Z]\\d+$")) {
            int y = normalized.charAt(0) - 'A';
            int x = Integer.parseInt(normalized.substring(1)) - 1;
            if (x < 0 || y < 0) {
                throw new NumberFormatException("Invalid gridId: " + gridId);
            }
            return new int[]{x, y};
        }

        String[] parts = normalized.replaceAll("[^0-9]", " ").trim().split("\\s+");
        if (parts.length >= 2) {
            return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
        }

        throw new NumberFormatException("Invalid gridId: " + gridId);
    }

    public ArtByIdResponse findByIdWithLocation(String artId) {
        return repository.findById(artId)
                .map(a -> new ArtByIdResponse(
                        a.getId(),
                        a.getTitle(),
                        a.getArtist(),
                        a.getDescription(),
                        a.getGridId(),
                a.getFloorId(),
                a.getX(),
                a.getY()
                ))
                .orElseThrow(() -> new RuntimeException("Art not found: " + artId));
    }

            public ArtInfoResponse.ArtDto findById(String artId) {
            return repository.findById(artId)
                .map(a -> new ArtInfoResponse.ArtDto(
                    a.getId(),
                    a.getTitle(),
                    a.getArtist(),
                    a.getDescription()
                ))
                .orElseThrow(() -> new RuntimeException("Art not found: " + artId));
            }

    public List<ArtInfoResponse.ArtDto> findAll() {
        return repository.findAll().stream()
                .map(a -> new ArtInfoResponse.ArtDto(
                        a.getId(),
                        a.getTitle(),
                        a.getArtist(),
                        a.getDescription()
                ))
                .toList();
    }

    private int manhattanDist(int x1, int y1, int x2, int y2) {
        return Math.abs(x1 - x2) + Math.abs(y1 - y2);
    }

    public Map<String, Object> getArtStatistics() {
        List<Art> allArts = repository.findAll();
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalArts", allArts.size());
        
        // Arts per floor
        Map<Integer, Long> artsByFloor = new HashMap<>();
        for (int f = 1; f <= props.getBuilding().getFloors(); f++) {
            artsByFloor.put(f, repository.countByFloorId(f));
        }
        stats.put("artsByFloor", artsByFloor);
        
        // Arts per grid WITH FULL DETAILS (not just count)
        Map<String, Object> artsByGrid = new HashMap<>();
        for (Art art : allArts) {
            String key = art.getFloorId() + ":" + art.getGridId();
            if (!artsByGrid.containsKey(key)) {
                artsByGrid.put(key, new ArrayList<>());
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> gridArts = (List<Map<String, Object>>) artsByGrid.get(key);
            Map<String, Object> artDetail = new HashMap<>();
            artDetail.put("id", art.getId());
            artDetail.put("title", art.getTitle());
            artDetail.put("artist", art.getArtist());
            artDetail.put("description", art.getDescription());
            artDetail.put("x", art.getX());
            artDetail.put("y", art.getY());
            gridArts.add(artDetail);
        }
        stats.put("artsByGrid", artsByGrid);
        stats.put("occupiedGrids", artsByGrid.size());
        stats.put("totalGrids", props.getBuilding().getFloors() 
                * props.getBuilding().getGridRows() * props.getBuilding().getGridCols());
        stats.put("gridCoverage", Math.round(100.0 * artsByGrid.size() / 
                (double)(props.getBuilding().getFloors() * props.getBuilding().getGridRows() * props.getBuilding().getGridCols())));
        
        return stats;
    }

    public Map<String, Object> getVectorStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("vectorDb", "qdrant");
        stats.put("vectorDbUrl", qdrantBaseUrl);
        stats.put("collectionName", qdrantCollection);

        try {
            // Collection metadata
            @SuppressWarnings("unchecked")
            Map<String, Object> collectionInfo = restTemplate.getForObject(
                    qdrantBaseUrl + "/collections/" + qdrantCollection, Map.class);

            long pointCount = 0;
            if (collectionInfo != null) {
                Object result = collectionInfo.get("result");
                if (result instanceof Map<?, ?> r) {
                    Object pc = r.get("points_count");
                    if (pc == null) pc = r.get("vectors_count");
                    if (pc instanceof Number n) pointCount = n.longValue();
                }
                stats.put("collectionExists", true);
            }
            stats.put("vectorCount", pointCount);
            stats.put("vectorBuildStatus", pointCount > 0 ? "built" : "empty");

            // Fetch fingerprint points (BLE signal grid cells) with their stored RSSI vectors.
            Map<String, Object> scrollBody = new HashMap<>();
            scrollBody.put("limit", 512);
            scrollBody.put("with_vector", true);
            scrollBody.put("with_payload", true);

            @SuppressWarnings("unchecked")
            Map<String, Object> scrollResp = restTemplate.postForObject(
                    qdrantBaseUrl + "/collections/" + qdrantCollection + "/points/scroll",
                    scrollBody, Map.class);

            List<Map<String, Object>> cells = new ArrayList<>();
            if (scrollResp != null && scrollResp.get("result") instanceof Map<?, ?> scrollResult) {
                Object pointsObj = scrollResult.get("points");
                if (pointsObj instanceof List<?> pointsList) {
                    for (Object p : pointsList) {
                        if (p instanceof Map<?, ?> point) {
                            Map<String, Object> cell = new HashMap<>();
                            cell.put("id", point.get("id"));
                            Object payload = point.get("payload");
                            if (payload instanceof Map<?, ?> pl) {
                                cell.put("gridId",  coalesce(pl, "gridId",  "grid_id",  "grid"));
                                cell.put("floorId", coalesce(pl, "floorId", "floor_id", "floor"));
                            }
                            Object vector = point.get("vector");
                            cell.put("vector", vector);
                            if (vector instanceof List<?> values) {
                                cell.put("vectorSize", values.size());
                            }
                            cells.add(cell);
                        }
                    }
                }
            }
            cells.sort((a, b) -> {
                int fa = toInt(a.get("floorId")), fb = toInt(b.get("floorId"));
                if (fa != fb) return fa - fb;
                return String.valueOf(a.get("gridId")).compareTo(String.valueOf(b.get("gridId")));
            });
            stats.put("fingerprintCells", cells);

        } catch (Exception e) {
            stats.put("collectionExists", false);
            stats.put("vectorBuildStatus", "offline");
            stats.put("vectorCount", 0);
            stats.put("fingerprintCells", List.of());
            stats.put("error", "Qdrant unavailable: " + e.getMessage());
        }

        return stats;
    }

    private Object coalesce(Map<?, ?> map, String... keys) {
        for (String k : keys) {
            Object v = map.get(k);
            if (v != null) return v;
        }
        return null;
    }

    private int toInt(Object v) {
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return 0; }
    }
}
