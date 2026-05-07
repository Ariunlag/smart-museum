package com.smartmuseum.core.orchestration.application;

import com.smartmuseum.core.client.web.dto.ProximityScanRequest;
import com.smartmuseum.core.client.ws.DeviceWebSocketHandler;
import com.smartmuseum.core.client.ws.SessionRegistry;
import com.smartmuseum.core.client.ws.dto.PushMessage;
import com.smartmuseum.core.integration.artinfo.http.dto.ArtByIdResult;
import com.smartmuseum.core.integration.artinfo.http.dto.ArtInfoResult;
import com.smartmuseum.core.integration.mqtt.HeatmapPublisher;
import com.smartmuseum.core.orchestration.port.ArtInfoClient;
import com.smartmuseum.core.registry.RegistryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Processes an artId received from a QR/NFC scan.
 *
 * Flow:
 *  1. Retrieve art + gridId from ArtInfo by artId
 *  2. Push the result to the user over WebSocket
 *  3. Update heatmap when location changes
 */
@Service
public class ProximityScanUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProximityScanUseCase.class);

    private final ArtInfoClient          artInfoClient;
    private final HeatmapPublisher       heatmapPublisher;
    private final DeviceWebSocketHandler wsHandler;
    private final SessionRegistry        sessionRegistry;
    private final RegistryService        registryService;

    public ProximityScanUseCase(ArtInfoClient artInfoClient,
                                HeatmapPublisher heatmapPublisher,
                                DeviceWebSocketHandler wsHandler,
                                SessionRegistry sessionRegistry,
                                RegistryService registryService) {
        this.artInfoClient    = artInfoClient;
        this.heatmapPublisher = heatmapPublisher;
        this.wsHandler        = wsHandler;
        this.sessionRegistry  = sessionRegistry;
        this.registryService  = registryService;
    }

    public void handle(ProximityScanRequest req) {
        log.info("Proximity scan: device={} artId={} source={}",
                req.deviceId(), req.artId(), req.source());

        // Verify ArtInfo service is ACTIVE before proceeding
        if (!registryService.isServiceActive("artinfo-service")) {
            log.warn("ArtInfo service is not ACTIVE, skipping proximity scan");
            wsHandler.push(req.deviceId(),
                    new PushMessage("service_unavailable", req.deviceId(),
                            Map.of("service", "artinfo-service",
                                   "source", req.source() != null ? req.source() : "QR"))
            );
            return;
        }

        // 1. Fetch art details (including grid location) from ArtInfo by artId
        ArtByIdResult art;
        try {
            art = artInfoClient.findById(req.artId());
        } catch (Exception e) {
            log.warn("ArtInfo lookup failed for artId={}: {}", req.artId(), e.getMessage());
            return;
        }

        if (art == null) {
            log.warn("Art not found: {}", req.artId());
            return;
        }

        // 2. Push location update to device over WebSocket.
        // Use HashMap instead of Map.of() because art coordinates can theoretically be null.
        Map<String, Object> payload = new HashMap<>();
        payload.put("floorId", art.floorId());
        payload.put("x",       art.x());
        payload.put("y",       art.y());
        payload.put("source",  req.source() != null ? req.source() : "QR");
        payload.put("arts",    List.of(new ArtInfoResult.ArtDto(
                art.artId(), art.title(), art.artist(), art.description())));
        wsHandler.push(req.deviceId(), new PushMessage("location_update", req.deviceId(), payload));

        // record last art info for admin UI and touch session
        try {
            if (art != null) {
                sessionRegistry.recordLastArt(req.deviceId(), art.artId(), art.title(), art.artist(), art.description(), req.source() != null ? req.source() : "QR");
            }
        } catch (Exception ex) {
            log.warn("Failed to record last art for {}: {}", req.deviceId(), ex.getMessage());
        }

        sessionRegistry.touch(req.deviceId());

        // 3. Publish heatmap event if the device moved to a new grid cell (atomic check-and-update)
        SessionRegistry.MoveResult move = sessionRegistry.compareAndMove(
                req.deviceId(), art.gridId(), art.floorId()
        );
        if (move.moved()) {
            heatmapPublisher.publish(
                    req.deviceId(), move.sequenceNum(),
                    art.gridId(), art.floorId(),
                    move.prevGridId(), move.prevFloorId()
            );
            log.info("Heatmap updated via {}: grid={} floor={}", req.source(), art.gridId(), art.floorId());
        }
    }
}
