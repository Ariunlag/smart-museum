package com.smartmuseum.positioning.vector;

import com.smartmuseum.positioning.config.MuseumProperties;
import com.smartmuseum.positioning.web.dto.BleReadingsRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VectorBuilderTest {

    private VectorBuilder vectorBuilder;

    @BeforeEach
    void setUp() {
        MuseumProperties props = new MuseumProperties();
        props.getBeacons().setTotal(5);
        props.getBeacons().setDefaultRssi(-100);

        MuseumProperties.FloorBeacon f1 = new MuseumProperties.FloorBeacon();
        f1.setId("floor-beacon-f1");
        f1.setFloor(1);

        MuseumProperties.FloorBeacon f2 = new MuseumProperties.FloorBeacon();
        f2.setId("floor-beacon-f2");
        f2.setFloor(2);

        props.getBeacons().setFloorBeacons(List.of(f1, f2));
        vectorBuilder = new VectorBuilder(props);
    }

    @Test
    void build_shouldUseConfiguredOrderAndDefaultRssiForMissingBeacons() {
        BleReadingsRequest req = new BleReadingsRequest(
                "dev-1",
                1L,
                List.of(
                        new BleReadingsRequest.BleReadingDto("floor-beacon-f2", -50),
                        new BleReadingsRequest.BleReadingDto("beacon-2", -80)
                )
        );

        List<Float> vector = vectorBuilder.build(req);

        assertEquals(5, vector.size());
        assertEquals(0.0f, vector.get(0)); // floor-beacon-f1 missing => default -100
        assertEquals(0.5f, vector.get(1)); // -50
        assertEquals(0.2f, vector.get(2)); // -80
    }

    @Test
    void build_shouldClampRssiIntoRange() {
        BleReadingsRequest req = new BleReadingsRequest(
                "dev-2",
                2L,
                List.of(
                        new BleReadingsRequest.BleReadingDto("floor-beacon-f1", 20),
                        new BleReadingsRequest.BleReadingDto("floor-beacon-f2", -150)
                )
        );

        List<Float> vector = vectorBuilder.build(req);

        assertEquals(1.0f, vector.get(0));
        assertEquals(0.0f, vector.get(1));
    }

    @Test
    void build_shouldKeepFirstReadingForDuplicateBeacon() {
        BleReadingsRequest req = new BleReadingsRequest(
                "dev-3",
                3L,
                List.of(
                        new BleReadingsRequest.BleReadingDto("beacon-2", -70),
                        new BleReadingsRequest.BleReadingDto("beacon-2", -40)
                )
        );

        List<Float> vector = vectorBuilder.build(req);

        assertEquals(0.3f, vector.get(2));
    }
}
