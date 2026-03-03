package com.smartmuseum.core.orchestration.application;

import com.smartmuseum.core.client.web.dto.BleIngestRequest;
import com.smartmuseum.core.client.ws.DeviceWebSocketHandler;
import com.smartmuseum.core.client.ws.SessionRegistry;
import com.smartmuseum.core.client.ws.dto.PushMessage;
import com.smartmuseum.core.integration.artinfo.http.dto.ArtInfoResult;
import com.smartmuseum.core.integration.mqtt.HeatmapPublisher;
import com.smartmuseum.core.integration.positioning.http.dto.PositioningResult;
import com.smartmuseum.core.orchestration.port.ArtInfoClient;
import com.smartmuseum.core.orchestration.port.PositioningClient;
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

    public ProcessBleUseCase(PositioningClient positioningClient,
                             ArtInfoClient artInfoClient,
                             HeatmapPublisher heatmapPublisher,
                             DeviceWebSocketHandler wsHandler,
                             SessionRegistry sessionRegistry) {
        this.positioningClient = positioningClient;
        this.artInfoClient     = artInfoClient;
        this.heatmapPublisher  = heatmapPublisher;
        this.wsHandler         = wsHandler;
        this.sessionRegistry   = sessionRegistry;
    }

    public void handle(BleIngestRequest req) {
        // 1. Positioning → gridId, floorId, x, y авна
        PositioningResult pos = positioningClient.locate(req);
        log.info("Position resolved: floor={} grid={}", pos.floorId(), pos.gridId());

        // 2. Байрлал өөрчлөгдсөн эсэхийг шалгана
        boolean locationChanged = sessionRegistry.hasLocationChanged(
                req.deviceId(), pos.gridId(), pos.floorId()
        );

        // 3. ArtInfo → ойролцоох art авна (service байхгүй бол хоосон)
        ArtInfoResult art;
        try {
            art = artInfoClient.findNearest(pos.gridId(), pos.floorId());
        } catch (Exception e) {
            log.warn("ArtInfo unavailable: {}", e.getMessage());
            art = new ArtInfoResult(List.of());
        }
        log.info("Arts found: {}", art.arts().size());

        // 4. WebSocket → утас руу байрлал + art явуулна
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

        // 5. Байрлал өөрчлөгдсөн бол heatmap update
        if (locationChanged) {
            String prevGridId = sessionRegistry.getLastGridId(req.deviceId());
            heatmapPublisher.publish(pos.gridId(), pos.floorId(), prevGridId);
            sessionRegistry.updateLocation(req.deviceId(), pos.gridId(), pos.floorId());
        }
    }
}