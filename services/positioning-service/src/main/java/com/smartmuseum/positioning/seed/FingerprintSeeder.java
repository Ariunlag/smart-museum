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
 * App эхлэхэд нэг удаа ажиллана.
 *
 * Beacon байрлал:
 *   20 beacon-г grid дээр жигд тараана
 *   Beacon i → (bRow, bCol) тооцоолно
 *
 * RSSI simulation:
 *   dist = grid(row,col) ↔ beacon(bRow,bCol) зай (метр)
 *   rssi = -40 - 20 * log10(dist + 1) + noise
 *   → бодит path-loss загварт ойролцоо
 *
 * Normalize:
 *   (rssi - (-100)) / 100 → [0.0 .. 1.0]
 */
@Component
public class FingerprintSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FingerprintSeeder.class);

    private final MuseumProperties props;
    private final QdrantRepository qdrant;
    // Beacon-уудын grid дээрх байрлал (бекон тус бүрт нэг байрлал)
    private int[][] beaconPositions;

    public FingerprintSeeder(MuseumProperties props, QdrantRepository qdrant) {
        this.props  = props;
        this.qdrant = qdrant;
    }

    @Override
    public void run(ApplicationArguments args) {
        int total  = props.getBeacons().getTotal();
        int floors = props.getBuilding().getFloors();
        int rows   = props.getBuilding().getGridRows();
        int cols   = props.getBuilding().getGridCols();

        // Beacon байрлалыг grid дээр жигд тараана
        initBeaconPositions(total, rows, cols);

        qdrant.ensureCollection(total);
        log.info("Seeding: {}x{} grid, {} floors, {} beacons", rows, cols, floors, total);

        List<Map<String, Object>> points = new ArrayList<>();
        long pointId = 1;

        for (int floor = 1; floor <= floors; floor++) {
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < cols; col++) {
                    String gridId   = gridLabel(row, col);
                    List<Float> vec = buildVector(total, row, col);

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
     * Beacon-уудыг grid дээр жигд тараана.
     * Жишээ: 20 beacon, 10x10 grid → 4x5 grid-р тараана
     */
    private void initBeaconPositions(int total, int rows, int cols) {
        beaconPositions = new int[total][2];
        int sqrtTotal = (int) Math.ceil(Math.sqrt(total));
        for (int i = 0; i < total; i++) {
            beaconPositions[i][0] = (int)((i / sqrtTotal) * ((double) rows / sqrtTotal));
            beaconPositions[i][1] = (int)((i % sqrtTotal) * ((double) cols / sqrtTotal));
        }
    }

    /**
     * Path-loss загвар ашиглан vector үүсгэнэ.
     * rssi = -40 - 20 * log10(dist_meters + 1) + noise
     */
    private List<Float> buildVector(int total, int row, int col) {
        int cellSize = props.getBuilding().getCellSizeMeters();
        Float[] vec  = new Float[total];

        for (int i = 0; i < total; i++) {
            int bRow = beaconPositions[i][0];
            int bCol = beaconPositions[i][1];

            // Grid cell зайг метрт хөрвүүлнэ
            double distMeters = Math.sqrt(
                    Math.pow((row - bRow) * cellSize, 2) +
                    Math.pow((col - bCol) * cellSize, 2)
            );

            // Path-loss, noise байхгүй — deterministik
            double rssi = -40 - 20 * Math.log10(distMeters + 1);
            rssi = Math.max(-100, Math.min(-30, rssi));

            vec[i] = (float)(rssi + 100) / 100f; // normalize [0..1]
        }
        return List.of(vec);
    }

    private String gridLabel(int row, int col) {
        return "" + (char)('A' + row) + (col + 1);
    }
}