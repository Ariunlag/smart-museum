package com.smartmuseum.core.client.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmuseum.core.client.ws.dto.PushMessage;
import com.smartmuseum.core.integration.mqtt.HeatmapPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Manages WebSocket connections for mobile devices.
 *
 * Connect:    ws://localhost:8080/ws?deviceId=phone-123
 * Disconnect: removes the session from the registry and publishes a leave event
 * Push:       call push(deviceId, payload) from use-case layer
 *
 * deviceId must match [a-zA-Z0-9_-]{3,64} — connections with invalid IDs are rejected.
 */
@Component
public class DeviceWebSocketHandler extends TextWebSocketHandler {

    private static final Logger  log               = LoggerFactory.getLogger(DeviceWebSocketHandler.class);
    private static final Pattern DEVICE_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{3,64}$");

    private final SessionRegistry  registry;
    private final ObjectMapper     mapper;
    private final HeatmapPublisher heatmapPublisher;

    public DeviceWebSocketHandler(SessionRegistry registry,
                                  ObjectMapper mapper,
                                  HeatmapPublisher heatmapPublisher) {
        this.registry         = registry;
        this.mapper           = mapper;
        this.heatmapPublisher = heatmapPublisher;
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) throws Exception {
        String deviceId = extractDeviceId(session);
        if (deviceId == null || deviceId.isBlank()) {
            log.warn("Rejected WebSocket connection: missing deviceId");
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        if (!DEVICE_ID_PATTERN.matcher(deviceId).matches()) {
            log.warn("Rejected WebSocket connection: invalid deviceId format '{}'", deviceId);
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        registry.addSession(deviceId, session);
        push(deviceId, new PushMessage("connected", deviceId, Map.of("status", "ok")));
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        String deviceId = extractDeviceId(session);
        if (deviceId == null) return;

        SessionRegistry.DeviceState state = registry.removeSession(deviceId);
        if (state != null && state.lastGridId() != null && state.lastFloorId() >= 0) {
            heatmapPublisher.publishLeave(
                    deviceId, state.lastEventSequence() + 1,
                    state.lastGridId(), state.lastFloorId()
            );
        }
    }

    public void push(String deviceId, PushMessage message) {
        WebSocketSession session = registry.getSession(deviceId);
        if (session == null || !session.isOpen()) return;
        try {
            String json = mapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            log.warn("WebSocket push failed for device {}: {}", deviceId, e.getMessage());
        }
    }

    private String extractDeviceId(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) return null;
        return UriComponentsBuilder
                .fromUri(uri)
                .build()
                .getQueryParams()
                .getFirst("deviceId");
    }
}
