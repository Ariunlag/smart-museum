package com.smartmuseum.core.orchestration.application;

import com.smartmuseum.core.client.web.dto.BleIngestRequest;
import com.smartmuseum.core.client.ws.DeviceWebSocketHandler;
import com.smartmuseum.core.client.ws.SessionRegistry;
import com.smartmuseum.core.client.ws.dto.PushMessage;
import com.smartmuseum.core.common.config.MuseumProperties;
import com.smartmuseum.core.integration.artinfo.http.dto.ArtInfoResult;
import com.smartmuseum.core.integration.mqtt.HeatmapPublisher;
import com.smartmuseum.core.integration.positioning.http.dto.PositioningResult;
import com.smartmuseum.core.orchestration.port.ArtInfoClient;
import com.smartmuseum.core.orchestration.port.PositioningClient;
import com.smartmuseum.core.registry.RegistryService;
import com.smartmuseum.core.registry.domain.ServiceRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ProcessBleUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessBleUseCase.class);

    private final PositioningClient      positioningClient;
    private final ArtInfoClient          artInfoClient;
    private final HeatmapPublisher       heatmapPublisher;
    private final DeviceWebSocketHandler wsHandler;
    private final SessionRegistry        sessionRegistry;
    private final MuseumProperties       props;
    private final RegistryService        registryService;

    public ProcessBleUseCase(PositioningClient positioningClient,
                             ArtInfoClient artInfoClient,
                             HeatmapPublisher heatmapPublisher,
                             DeviceWebSocketHandler wsHandler,
                             SessionRegistry sessionRegistry,
                             MuseumProperties props,
                             RegistryService registryService) {
        this.positioningClient = positioningClient;
        this.artInfoClient     = artInfoClient;
        this.heatmapPublisher  = heatmapPublisher;
        this.wsHandler         = wsHandler;
        this.sessionRegistry   = sessionRegistry;
        this.props             = props;
        this.registryService   = registryService;
    }

    public void handle(BleIngestRequest req) {

        // 1. Positioning enabled + ACTIVE шалгана
        if (!props.getServices().getPositioning().isEnabled()) {
            log.warn("Positioning disabled in config, skipping");
            return;
        }
        if (!registryService.isServiceActive("positioning-service")) {
            log.warn("Positioning service is not ACTIVE in registry, skipping");
            return;
        }

        PositioningResult pos = positioningClient.locate(req);
        log.info("Position resolved: floor={} grid={}", pos.floorId(), pos.gridId());

        // 2. ArtInfo (optional — disabled бол хоосон буцаана)
        ArtInfoResult art;
        if (props.getServices().getArtinfo().isEnabled()
                && registryService.isServiceActive("artinfo-service")) {
            try {
                art = artInfoClient.findNearest(pos.gridId(), pos.floorId());
                log.info("Arts found: {}", art.arts().size());
            } catch (Exception e) {
                log.warn("ArtInfo unavailable: {}", e.getMessage());
                art = new ArtInfoResult(List.of());
            }
        } else {
            log.info("ArtInfo skipped (disabled or not active)");
            art = new ArtInfoResult(List.of());
        }

        // 3. WebSocket → device-д push
        wsHandler.push(req.deviceId(),
                new PushMessage(
                        "location_update",
                        req.deviceId(),
                        Map.of(
                                "floorId", pos.floorId(),
                                "x",       pos.x(),
                                "y",       pos.y(),
                                "arts",    art.arts()
                        )
                )
        );

                sessionRegistry.touch(req.deviceId());

        // 4. Heatmap MQTT (optional)
        if (props.getServices().getHeatmap().isEnabled()
                && registryService.isServiceActive("heatmap-service")) {
            boolean changed = sessionRegistry.hasLocationChanged(
                    req.deviceId(), pos.gridId(), pos.floorId()
            );
            if (changed) {
                String prevGridId = sessionRegistry.getLastGridId(req.deviceId());
                Integer prevFloorId = sessionRegistry.getLastFloorId(req.deviceId());
                heatmapPublisher.publish(pos.gridId(), pos.floorId(), prevGridId, prevFloorId);
                sessionRegistry.updateLocation(req.deviceId(), pos.gridId(), pos.floorId());
            }
        }
    }
}