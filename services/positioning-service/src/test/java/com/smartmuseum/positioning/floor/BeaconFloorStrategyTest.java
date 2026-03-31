package com.smartmuseum.positioning.floor;

import com.smartmuseum.positioning.config.MuseumProperties;
import com.smartmuseum.positioning.web.dto.BleReadingsRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BeaconFloorStrategyTest {

    private BeaconFloorStrategy strategy;

    @BeforeEach
    void setUp() {
        MuseumProperties props = new MuseumProperties();

        MuseumProperties.FloorBeacon f1 = new MuseumProperties.FloorBeacon();
        f1.setId("floor-beacon-f1");
        f1.setFloor(1);

        MuseumProperties.FloorBeacon f2 = new MuseumProperties.FloorBeacon();
        f2.setId("floor-beacon-f2");
        f2.setFloor(2);

        props.getBeacons().setFloorBeacons(List.of(f1, f2));
        strategy = new BeaconFloorStrategy(props);
    }

    @Test
    void detect_shouldReturnFloorFromKnownFloorBeacon() {
        BleReadingsRequest req = new BleReadingsRequest(
                "dev-1", 1L,
                List.of(
                        new BleReadingsRequest.BleReadingDto("beacon-9", -60),
                        new BleReadingsRequest.BleReadingDto("floor-beacon-f2", -55)
                )
        );

        assertEquals(2, strategy.detect(req));
    }

    @Test
    void detect_shouldDefaultToFirstFloorWhenNoFloorBeaconPresent() {
        BleReadingsRequest req = new BleReadingsRequest(
                "dev-2", 2L,
                List.of(new BleReadingsRequest.BleReadingDto("beacon-5", -65))
        );

        assertEquals(1, strategy.detect(req));
    }
}
