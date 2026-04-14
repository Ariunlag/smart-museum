package com.smartmuseum.core.client.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmuseum.core.client.ws.dto.PushMessage;
import com.smartmuseum.core.integration.mqtt.HeatmapPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Map;

/**
 * Manages WebSocket connections for mobile devices.
 *
 * Connect:    ws://localhost:8080/ws?deviceId=phone-123
 * Disconnect: removes the session from the registry
 * Push:       call push(deviceId, payload)
 */
@Component
public class DeviceWebSocketHandler extends TextWebSocketHandler {

    private final SessionRegistry registry;
    private final ObjectMapper mapper;
    private final HeatmapPublisher heatmapPublisher;

    public DeviceWebSocketHandler(SessionRegistry registry,
                                  ObjectMapper mapper,
                                  HeatmapPublisher heatmapPublisher) {
        this.registry = registry;
        this.mapper = mapper;
        this.heatmapPublisher = heatmapPublisher;
    }

    // ── Утас холбогдоход ────────────────────────────────
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String deviceId = extractDeviceId(session);
        if (deviceId == null || deviceId.isBlank()) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        registry.addSession(deviceId, session);

        // Welcome message явуулна
        push(deviceId, new PushMessage(
                "connected",
                deviceId,
                Map.of("status", "ok")
        ));
    }

    // ── Утас тасрахад ───────────────────────────────────
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String deviceId = extractDeviceId(session);
        if (deviceId == null) {
            return;
        }

        SessionRegistry.DeviceState state = registry.removeSession(deviceId);
        if (state != null && state.lastGridId() != null && state.lastFloorId() >= 0) {
            heatmapPublisher.publishLeave(state.lastGridId(), state.lastFloorId());
        }
    }

    // ── Утас руу push хийх ──────────────────────────────
    public void push(String deviceId, PushMessage message) {
        WebSocketSession session = registry.getSession(deviceId);
        if (session == null || !session.isOpen()) return;
        try {
            String json = mapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(json));
        } catch (IOException ignored) {}
    }

    // ── URL-с deviceId авна ─────────────────────────────
    // ws://localhost:8080/ws?deviceId=phone-123
    private String extractDeviceId(WebSocketSession session) {
        if (session.getUri() == null) return null;
        return UriComponentsBuilder
                .fromUri(session.getUri())
                .build()
                .getQueryParams()
                .getFirst("deviceId");
    }
}