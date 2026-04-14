package com.smartmuseum.core.orchestration.application;

import com.smartmuseum.core.client.web.dto.ProximityScanRequest;
import com.smartmuseum.core.client.ws.DeviceWebSocketHandler;
import com.smartmuseum.core.client.ws.SessionRegistry;
import com.smartmuseum.core.client.ws.dto.PushMessage;
import com.smartmuseum.core.integration.artinfo.http.dto.ArtByIdResult;
import com.smartmuseum.core.integration.artinfo.http.dto.ArtInfoResult;
import com.smartmuseum.core.integration.mqtt.HeatmapPublisher;
import com.smartmuseum.core.orchestration.port.ArtInfoClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
import com.smartmuseum.core.registry.RegistryService;

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

        // ArtInfo service ACTIVE эсэхийг шалгана
        if (!registryService.isServiceActive("artinfo-service")) {
            log.warn("ArtInfo service is not ACTIVE, skipping proximity scan");
            wsHandler.push(req.deviceId(),
                    new PushMessage("service_unavailable", req.deviceId(),
                            java.util.Map.of("service", "artinfo-service",
                                    "source", req.source() != null ? req.source() : "QR"))
            );
            return;
        }

        // 1. ArtInfo-с artId-р art + gridId авна
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

        // 2. WebSocket → user-д push
        wsHandler.push(req.deviceId(),
                new PushMessage(
                        "location_update",
                        req.deviceId(),
                        Map.of(
                                "floorId", art.floorId(),
                                "x",       art.x(),
                                "y",       art.y(),
                                "source",  req.source() != null ? req.source() : "QR",
                                "arts",    List.of(new ArtInfoResult.ArtDto(
                                        art.artId(),
                                        art.title(),
                                        art.artist(),
                                        art.description()
                                ))
                        )
                )
        );

        sessionRegistry.touch(req.deviceId());

        // 3. Heatmap update — байрлал өөрчлөгдсөн бол
        boolean changed = sessionRegistry.hasLocationChanged(
                req.deviceId(), art.gridId(), art.floorId()
        );
        if (changed) {
            String prevGridId = sessionRegistry.getLastGridId(req.deviceId());
                        Integer prevFloorId = sessionRegistry.getLastFloorId(req.deviceId());
                        heatmapPublisher.publish(art.gridId(), art.floorId(), prevGridId, prevFloorId);
            sessionRegistry.updateLocation(req.deviceId(), art.gridId(), art.floorId());
            log.info("Heatmap updated via {}: grid={} floor={}",
                    req.source(), art.gridId(), art.floorId());
        }
    }
}