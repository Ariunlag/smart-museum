package com.smartmuseum.positioning.floor;

import com.smartmuseum.positioning.config.MuseumProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Resolves and returns the floor detection strategy configured in YAML.
 * strategy: beacon -> beaconFloorStrategy bean
 * strategy: manual -> manualFloorStrategy bean (future extension)
 */
@Component
public class FloorDetectionFactory {

    private final Map<String, FloorDetectionStrategy> strategies;
    private final MuseumProperties props;

    public FloorDetectionFactory(Map<String, FloorDetectionStrategy> strategies,
                                 MuseumProperties props) {
        this.strategies = strategies;
        this.props      = props;
    }

    public FloorDetectionStrategy get() {
        String beanName = props.getFloor().getStrategy() + "FloorStrategy";
        FloorDetectionStrategy strategy = strategies.get(beanName);
        if (strategy == null) {
            throw new IllegalStateException(
                    "Unknown floor strategy: " + props.getFloor().getStrategy()
            );
        }
        return strategy;
    }
}