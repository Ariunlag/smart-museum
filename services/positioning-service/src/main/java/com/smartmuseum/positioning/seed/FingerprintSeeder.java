package com.smartmuseum.positioning.seed;

import com.smartmuseum.positioning.config.MuseumProperties;
import com.smartmuseum.positioning.qdrant.QdrantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Runs once at application startup.
 *
 * Beacon placement:
 *   Distributes beacons over the grid
 *   Computes beacon i -> (bRow, bCol)
 *
 * RSSI simulation:
 *   dist = distance in meters between grid(row,col) and beacon(bRow,bCol)
 *   rssi = txPower - 10 * n * log10(dist / d0)
 *   txPower/n/maxRange are loaded from layout.yml
 *
 * Normalization:
 *   (rssi - (-100)) / 100 -> [0.0 .. 1.0]
 */
@Component
public class FingerprintSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FingerprintSeeder.class);

    private final MuseumProperties props;
    private final QdrantRepository qdrant;
    private List<SeedBeacon> seedBeacons;

    public FingerprintSeeder(MuseumProperties props, QdrantRepository qdrant) {
        this.props  = props;
        this.qdrant = qdrant;
    }

    @Override
    public void run(ApplicationArguments args) {
        int total  = resolveBeaconCount();
        int floors = props.getBuilding().getFloors();
        int rows   = props.getBuilding().getGridRows();
        int cols   = props.getBuilding().getGridCols();

        initBeaconPositions(total, rows, cols);

        qdrant.ensureCollection(total);
        log.info("Seeding: {}x{} grid, {} floors, {} beacons", rows, cols, floors, total);

        List<Map<String, Object>> points = new ArrayList<>();
        long pointId = 1;

        for (int floor = 1; floor <= floors; floor++) {
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < cols; col++) {
                    String gridId   = gridLabel(row, col);
                    List<Float> vec = buildVector(total, floor, row, col);

                    Map<String, Object> point = new HashMap<>();
                    point.put("id", pointId++);
                    point.put("vector", vec);
                    point.put("payload", Map.of(
                            "gridId",  gridId,
                            "floorId", floor,
                            "x",       col,
                            "y",       row
                    ));
                    points.add(point);
                }
            }
        }

        qdrant.upsert(points);
        log.info("Seed complete: {} points", points.size());
    }

    /**
     * Loads beacon positions from external layout configuration.
     * Falls back to deterministic uniform distribution when configuration is empty.
     */
    private void initBeaconPositions(int total, int rows, int cols) {
        var configured = props.getBeacons().getPositions();
        if (configured != null && !configured.isEmpty()) {
            int maxConfiguredX = configured.stream().mapToInt(p -> Math.max(0, p.getX())).max().orElse(0);
            int maxConfiguredY = configured.stream().mapToInt(p -> Math.max(0, p.getY())).max().orElse(0);
            double scaleX = maxConfiguredX > 0 ? (double) (cols - 1) / (double) maxConfiguredX : 1.0;
            double scaleY = maxConfiguredY > 0 ? (double) (rows - 1) / (double) maxConfiguredY : 1.0;

            this.seedBeacons = configured.stream()
                .map(p -> new SeedBeacon(
                    p.getId(),
                    p.getFloor(),
                    clamp((int) Math.round(p.getY() * scaleY), 0, Math.max(0, rows - 1)),
                    clamp((int) Math.round(p.getX() * scaleX), 0, Math.max(0, cols - 1)),
                    p.getTxPowerDb(),
                    p.getPathLossExponent(),
                        p.getMaxRangeMeters(),
                        p.getCutoffRssi()
                ))
                    .toList();
                log.info("Using {} beacons from layout config (scaled by x={}, y={})",
                    seedBeacons.size(), scaleX, scaleY);
            return;
        }

        // Fallback: хуучин deterministic жигд тархаалт
        this.seedBeacons = new ArrayList<>();
        int sqrtTotal = (int) Math.ceil(Math.sqrt(total));
        for (int i = 0; i < total; i++) {
            int row = (int) ((i / sqrtTotal) * ((double) rows / sqrtTotal));
            int col = (int) ((i % sqrtTotal) * ((double) cols / sqrtTotal));
            seedBeacons.add(new SeedBeacon("beacon-" + i, 1, row, col, null, null, null, null));
        }
        log.warn("No beacon positions found in layout file, fallback distribution is used");
    }

    /**
     * Builds a fingerprint vector using the path-loss model.
     * rssi = txPower - 10 * n * log10(dist / d0)
     */
    private List<Float> buildVector(int total, int floor, int row, int col) {
        int cellSize = props.getBuilding().getCellSizeMeters();
        int floorHeight = props.getBuilding().getFloorHeightMeters();
        var model = props.getBeacons().getModel();
        int minRssi = model.getMinRssi();
        int maxRssi = model.getMaxRssi();
        double d0 = Math.max(0.1, model.getReferenceDistanceMeters());
        Float[] vec  = new Float[total];

        for (int i = 0; i < total; i++) {
            SeedBeacon beacon = seedBeacons.get(i);
            int bRow = beacon.row();
            int bCol = beacon.col();
            int bFloor = beacon.floor();

            // Grid cell зайг метрт хөрвүүлнэ
            double dzMeters = (floor - bFloor) * floorHeight;
            double distMeters = Math.sqrt(
                    Math.pow((row - bRow) * cellSize, 2) +
                    Math.pow((col - bCol) * cellSize, 2) +
                    Math.pow(dzMeters, 2)
            );

            double txPowerDb = beacon.txPowerDb() != null ? beacon.txPowerDb() : model.getTxPowerDb();
            double n = beacon.pathLossExponent() != null ? beacon.pathLossExponent() : model.getPathLossExponent();
            double maxRange = beacon.maxRangeMeters() != null ? beacon.maxRangeMeters() : model.getMaxRangeMeters();
            int cutoffRssi = beacon.cutoffRssi() != null ? beacon.cutoffRssi() : model.getCutoffRssi();

            double rssi;
            if (distMeters > maxRange) {
                rssi = minRssi;
            } else {
                double effectiveDist = Math.max(d0, distMeters);
                rssi = txPowerDb - (10.0 * n * Math.log10(effectiveDist / d0));
                if (rssi < cutoffRssi) {
                    rssi = minRssi;
                }
            }
            rssi = Math.max(minRssi, Math.min(maxRssi, rssi));

            vec[i] = normalize(rssi, minRssi, maxRssi);
        }
        return List.of(vec);
    }

    private float normalize(double rssi, int minRssi, int maxRssi) {
        if (maxRssi <= minRssi) {
            return 0f;
        }
        return (float) ((rssi - minRssi) / (maxRssi - minRssi));
    }

    private String gridLabel(int row, int col) {
        return "" + (char)('A' + row) + (col + 1);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private int resolveBeaconCount() {
        var positions = props.getBeacons().getPositions();
        if (positions != null && !positions.isEmpty()) {
            return positions.size();
        }
        return props.getBeacons().getTotal();
    }

    private record SeedBeacon(String id,
                              int floor,
                              int row,
                              int col,
                              Double txPowerDb,
                              Double pathLossExponent,
                              Double maxRangeMeters,
                              Integer cutoffRssi) {}
}