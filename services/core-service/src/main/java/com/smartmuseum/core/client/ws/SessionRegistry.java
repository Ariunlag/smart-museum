package com.smartmuseum.core.client.ws;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionRegistry {

    public record DeviceState(
            WebSocketSession session,
            String lastGridId,
            int    lastFloorId
    ) {}

    private final Map<String, DeviceState> states = new ConcurrentHashMap<>();

    // ── WebSocketHandler дуудна ──────────────────────────
    public void addSession(String deviceId, WebSocketSession session) {
        states.put(deviceId, new DeviceState(session, null, -1));
    }

    public void removeSession(String deviceId) {
        states.remove(deviceId);
    }

    public WebSocketSession getSession(String deviceId) {
        DeviceState s = states.get(deviceId);
        return s != null ? s.session() : null;
    }

    // ── ProcessBleUseCase дуудна ─────────────────────────
    public boolean hasLocationChanged(String deviceId, String gridId, int floorId) {
        DeviceState s = states.get(deviceId);
        if (s == null) return true;
        return !gridId.equals(s.lastGridId()) || floorId != s.lastFloorId();
    }

    public String getLastGridId(String deviceId) {
        DeviceState s = states.get(deviceId);
        return s != null ? s.lastGridId() : null;
    }

    public void updateLocation(String deviceId, String gridId, int floorId) {
        DeviceState s = states.get(deviceId);
        if (s == null) return;
        states.put(deviceId, new DeviceState(s.session(), gridId, floorId));
    }
}