package com.smartmuseum.core.orchestration.application;

import com.smartmuseum.core.client.ws.SessionRegistry;
import com.smartmuseum.core.common.config.MuseumProperties;
import com.smartmuseum.core.integration.mqtt.HeatmapPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class InactiveDeviceCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(InactiveDeviceCleanupJob.class);

    private final SessionRegistry sessionRegistry;
    private final HeatmapPublisher heatmapPublisher;
    private final MuseumProperties props;

    public InactiveDeviceCleanupJob(SessionRegistry sessionRegistry,
                                    HeatmapPublisher heatmapPublisher,
                                    MuseumProperties props) {
        this.sessionRegistry = sessionRegistry;
        this.heatmapPublisher = heatmapPublisher;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${museum.websocket.cleanup-interval-ms}")
    public void evictInactiveDevices() {
        long timeout = props.getWebsocket().getInactivityTimeoutMs();
        var removed = sessionRegistry.removeInactive(timeout);

        removed.forEach(device -> {
            heatmapPublisher.publishLeave(device.lastGridId(), device.lastFloorId());
            log.info("Inactive device removed from heatmap: deviceId={} floor={} grid={}",
                    device.deviceId(), device.lastFloorId(), device.lastGridId());
        });
    }
}
