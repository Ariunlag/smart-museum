package com.smartmuseum.positioning.vector;

import com.smartmuseum.positioning.config.MuseumProperties;
import com.smartmuseum.positioning.web.dto.BleReadingsRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * BLE readings → normalized float vector[beaconTotal] үүсгэнэ.
 *
 * Beacon order: floor-beacon-ууд эхэлж, дараа beacon-0..N
 * Ирээгүй beacon → defaultRssi (-100)
 * Normalize: (rssi - (-100)) / (0 - (-100)) → [0.0 .. 1.0]
 */
@Component
public class VectorBuilder {

    private static final int RSSI_MIN = -100;
    private static final int RSSI_MAX =    0;

    private final int          total;
    private final int          defaultRssi;
    private final List<String> beaconOrder;

    public VectorBuilder(MuseumProperties props) {
        this.total       = props.getBeacons().getTotal();
        this.defaultRssi = props.getBeacons().getDefaultRssi();

        // Floor beacon-уудыг эхэнд тавина
        this.beaconOrder = new ArrayList<>(
                props.getBeacons().getFloorBeacons().stream()
                        .map(MuseumProperties.FloorBeacon::getId)
                        .toList()
        );

        // Үлдсэн slot-уудад beacon-0..N нэр үүсгэнэ
        for (int i = beaconOrder.size(); i < total; i++) {
            beaconOrder.add("beacon-" + i);
        }
    }

    public List<Float> build(BleReadingsRequest req) {
        Map<String, Integer> rssiMap = req.readings().stream()
                .collect(Collectors.toMap(
                        BleReadingsRequest.BleReadingDto::beaconId,
                        BleReadingsRequest.BleReadingDto::rssi,
                        (a, b) -> a // давхардсан beacon бол эхнийхийг авна
                ));

        List<Float> vec = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            int rssi = rssiMap.getOrDefault(beaconOrder.get(i), defaultRssi);
            vec.add(normalize(rssi));
        }
        return vec;
    }

    public List<String> getBeaconOrder() {
        return beaconOrder;
    }

    private float normalize(int rssi) {
        int clamped = Math.max(RSSI_MIN, Math.min(RSSI_MAX, rssi));
        return (float)(clamped - RSSI_MIN) / (RSSI_MAX - RSSI_MIN);
    }
}