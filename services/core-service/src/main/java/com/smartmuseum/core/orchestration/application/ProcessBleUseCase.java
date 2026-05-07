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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
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

        // 1. Verify positioning is enabled and the service is ACTIVE in the registry
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

        // 2. ArtInfo lookup (optional — returns empty list when disabled or unavailable)
        ArtInfoResult art;
        boolean positionKnown = pos.gridId() != null && !pos.gridId().equalsIgnoreCase("UNKNOWN");
        if (positionKnown
                && props.getServices().getArtinfo().isEnabled()
                && registryService.isServiceActive("artinfo-service")) {
            try {
                art = artInfoClient.findNearest(pos.gridId(), pos.floorId());
                log.info("Arts found: {}", art.arts().size());
            } catch (Exception e) {
                log.warn("ArtInfo unavailable: {}", e.getMessage());
                art = new ArtInfoResult(List.of());
            }
        } else {
            if (!positionKnown) log.warn("Position unknown for device {}, skipping ArtInfo lookup", req.deviceId());
            else log.info("ArtInfo skipped (disabled or not active)");
            art = new ArtInfoResult(List.of());
        }

        // 3. Push location update to device over WebSocket.
        // Use HashMap instead of Map.of() because coordinates can theoretically be null.
        Map<String, Object> payload = new HashMap<>();
        payload.put("floorId", pos.floorId());
        payload.put("x",       pos.x());
        payload.put("y",       pos.y());
        payload.put("arts",    art.arts());
        wsHandler.push(req.deviceId(), new PushMessage("location_update", req.deviceId(), payload));

        if (!art.arts().isEmpty()) {
            ArtInfoResult.ArtDto nearest = art.arts().get(0);
            try {
                sessionRegistry.recordLastArt(req.deviceId(),
                        nearest.artId(), nearest.title(), nearest.artist(), nearest.description(), "BLE");
            } catch (Exception e) {
                log.warn("Failed to record last art for {}: {}", req.deviceId(), e.getMessage());
            }
        }

        sessionRegistry.touch(req.deviceId());

        // 4. Heatmap MQTT event (optional, published only when grid cell changes)
        if (props.getServices().getHeatmap().isEnabled()
                && registryService.isServiceActive("heatmap-service")) {
            SessionRegistry.MoveResult move = sessionRegistry.compareAndMove(
                    req.deviceId(), pos.gridId(), pos.floorId()
            );
            if (move.moved()) {
                heatmapPublisher.publish(
                        req.deviceId(), move.sequenceNum(),
                        pos.gridId(), pos.floorId(),
                        move.prevGridId(), move.prevFloorId()
                );
            }
        }
    }
}
