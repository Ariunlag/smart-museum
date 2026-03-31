package com.smartmuseum.positioning.floor;

import com.smartmuseum.positioning.config.MuseumProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class FloorDetectionFactoryTest {

    private MuseumProperties props;

    @BeforeEach
    void setUp() {
        props = new MuseumProperties();
    }

    @Test
    void get_shouldReturnConfiguredStrategy() {
        FloorDetectionStrategy beaconStrategy = req -> 1;
        FloorDetectionFactory factory = new FloorDetectionFactory(
                Map.of("beaconFloorStrategy", beaconStrategy),
                props
        );

        props.getFloor().setStrategy("beacon");

        assertSame(beaconStrategy, factory.get());
    }

    @Test
    void get_shouldThrowForUnknownStrategy() {
        FloorDetectionFactory factory = new FloorDetectionFactory(Map.of(), props);
        props.getFloor().setStrategy("unknown");

        IllegalStateException ex = assertThrows(IllegalStateException.class, factory::get);
        assertTrue(ex.getMessage().contains("unknown"));
    }
}
