package com.smartmuseum.positioning.floor;

import com.smartmuseum.positioning.config.MuseumProperties;
import com.smartmuseum.positioning.web.dto.BleReadingsRequest;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Detects floor using floor beacons.
 * Searches among floor beacons listed in YAML.
 * Falls back to floor 1 when no anchor beacon is found.
 */
@Component("beaconFloorStrategy")
public class BeaconFloorStrategy implements FloorDetectionStrategy {

    private final Map<String, Integer> floorMap;

    public BeaconFloorStrategy(MuseumProperties props) {
        var anchorsFromPositions = props.getBeacons().getPositions().stream()
            .filter(p -> p.getRole() != null && p.getRole().equalsIgnoreCase("floor-anchor"))
            .collect(Collectors.toMap(
                MuseumProperties.BeaconPosition::getId,
                MuseumProperties.BeaconPosition::getFloor,
                (a, b) -> a
            ));

        if (!anchorsFromPositions.isEmpty()) {
            this.floorMap = anchorsFromPositions;
            return;
        }

        this.floorMap = props.getBeacons().getFloorBeacons().stream()
            .filter(f -> f.getId() != null)
            .collect(Collectors.toMap(
                MuseumProperties.FloorBeacon::getId,
                MuseumProperties.FloorBeacon::getFloor,
                (a, b) -> a
            ));
    }

    @Override
    public int detect(BleReadingsRequest req) {
        return req.readings().stream()
                .filter(r -> floorMap.containsKey(r.beaconId()))
                .max(Comparator.comparingInt(r -> Objects.requireNonNullElse(r.rssi(), -100)))
                .map(r -> floorMap.get(r.beaconId()))
                .orElse(1); // default: 1-р давхар
    }
}