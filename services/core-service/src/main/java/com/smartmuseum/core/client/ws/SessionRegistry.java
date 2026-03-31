package com.smartmuseum.core.client.ws;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionRegistry {

    public record DeviceState(
            WebSocketSession session,
            String lastGridId,
            int    lastFloorId,
            long   lastSeenAt
    ) {}

        public record RemovedDevice(
            String deviceId,
            String lastGridId,
            int lastFloorId
        ) {}

    private final Map<String, DeviceState> states = new ConcurrentHashMap<>();

    // ── WebSocketHandler дуудна ──────────────────────────
    public void addSession(String deviceId, WebSocketSession session) {
        DeviceState existing = states.get(deviceId);
        String lastGridId = existing != null ? existing.lastGridId() : null;
        int lastFloorId = existing != null ? existing.lastFloorId() : -1;
        states.put(deviceId, new DeviceState(session, lastGridId, lastFloorId, System.currentTimeMillis()));
    }

    public DeviceState removeSession(String deviceId) {
        return states.remove(deviceId);
    }

    public WebSocketSession getSession(String deviceId) {
        DeviceState s = states.get(deviceId);
        return s != null ? s.session() : null;
    }

    // ── ProcessBleUseCase дуудна ─────────────────────────
    public boolean hasLocationChanged(String deviceId, String gridId, int floorId) {
        DeviceState s = states.get(deviceId);
        if (s == null) return true;
        // Null-safe comparison to avoid NullPointerException when gridId is null
        boolean gridChanged = gridId == null ? s.lastGridId() != null : !gridId.equals(s.lastGridId());
        return gridChanged || floorId != s.lastFloorId();
    }

    public String getLastGridId(String deviceId) {
        DeviceState s = states.get(deviceId);
        return s != null ? s.lastGridId() : null;
    }

    public Integer getLastFloorId(String deviceId) {
        DeviceState s = states.get(deviceId);
        return s != null && s.lastFloorId() >= 0 ? s.lastFloorId() : null;
    }

    public void updateLocation(String deviceId, String gridId, int floorId) {
        DeviceState s = states.get(deviceId);
        WebSocketSession session = s != null ? s.session() : null;
        states.put(deviceId, new DeviceState(session, gridId, floorId, System.currentTimeMillis()));
    }

    public void touch(String deviceId) {
        states.computeIfPresent(deviceId, (id, s) ->
                new DeviceState(s.session(), s.lastGridId(), s.lastFloorId(), System.currentTimeMillis())
        );
    }

    public List<RemovedDevice> removeInactive(long inactiveTimeoutMs) {
        long now = System.currentTimeMillis();
        List<RemovedDevice> removed = new ArrayList<>();

        states.forEach((deviceId, state) -> {
            if (now - state.lastSeenAt() > inactiveTimeoutMs) {
                DeviceState deleted = states.remove(deviceId);
                if (deleted != null && deleted.lastGridId() != null && deleted.lastFloorId() >= 0) {
                    removed.add(new RemovedDevice(deviceId, deleted.lastGridId(), deleted.lastFloorId()));
                }
            }
        });

        return removed;
    }
}