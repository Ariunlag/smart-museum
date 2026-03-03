package com.smartmuseum.positioning.floor;

import com.smartmuseum.positioning.config.MuseumProperties;
import com.smartmuseum.positioning.web.dto.BleReadingsRequest;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Floor beacon-р давхар тодорхойлно.
 * yml-д listed floor-beacon-уудаас хайна.
 * Олдохгүй бол default: 1-р давхар.
 */
@Component("beaconFloorStrategy")
public class BeaconFloorStrategy implements FloorDetectionStrategy {

    private final Map<String, Integer> floorMap;

    public BeaconFloorStrategy(MuseumProperties props) {
        this.floorMap = props.getBeacons().getFloorBeacons().stream()
                .collect(Collectors.toMap(
                        MuseumProperties.FloorBeacon::getId,
                        MuseumProperties.FloorBeacon::getFloor
                ));
    }

    @Override
    public int detect(BleReadingsRequest req) {
        return req.readings().stream()
                .filter(r -> floorMap.containsKey(r.beaconId()))
                .mapToInt(r -> floorMap.get(r.beaconId()))
                .findFirst()
                .orElse(1); // default: 1-р давхар
    }
}