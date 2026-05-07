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
            long   lastSeenAt,
            long   lastEventSequence,
            String lastArtId,
            String lastArtTitle,
            String lastArtArtist,
            String lastArtDescription,
            String lastArtSource,
            long   lastArtAt
    ) {}

        public record RemovedDevice(
            String deviceId,
            String lastGridId,
            int lastFloorId,
            long lastEventSequence
        ) {}

    public record AdminDeviceInfo(
            String deviceId,
            String status,
            String lastGridId,
            int lastFloorId,
            long lastSeenAt,
            long lastEventSequence,
            String lastArtId,
            String lastArtTitle,
            String lastArtArtist,
            String lastArtDescription,
            String lastArtSource,
            long lastArtAt
    ) {}

    private final Map<String, DeviceState> states = new ConcurrentHashMap<>();

    // ── WebSocketHandler ──────────────────────────────────
    public void addSession(String deviceId, WebSocketSession session) {
        DeviceState existing = states.get(deviceId);
        String lastGridId = existing != null ? existing.lastGridId() : null;
        int lastFloorId = existing != null ? existing.lastFloorId() : -1;
        long lastEventSequence = existing != null ? existing.lastEventSequence() : 0;
        String lastArtId = existing != null ? existing.lastArtId() : null;
        String lastArtTitle = existing != null ? existing.lastArtTitle() : null;
        String lastArtArtist = existing != null ? existing.lastArtArtist() : null;
        String lastArtDescription = existing != null ? existing.lastArtDescription() : null;
        String lastArtSource = existing != null ? existing.lastArtSource() : null;
        long lastArtAt = existing != null ? existing.lastArtAt() : 0L;
        states.put(deviceId, new DeviceState(session, lastGridId, lastFloorId, System.currentTimeMillis(), lastEventSequence,
                lastArtId, lastArtTitle, lastArtArtist, lastArtDescription, lastArtSource, lastArtAt));
    }

    public DeviceState removeSession(String deviceId) {
        return states.remove(deviceId);
    }

    public WebSocketSession getSession(String deviceId) {
        DeviceState s = states.get(deviceId);
        return s != null ? s.session() : null;
    }

    // ── Atomic location move used by use cases ───────────
    public record MoveResult(boolean moved, String prevGridId, Integer prevFloorId, long sequenceNum) {}

    /**
     * Atomically checks whether the device has moved, captures the previous location,
     * increments the event sequence, and updates the stored state — all in one CAS operation.
     * Callers should use this instead of the old hasLocationChanged / getLastGrid* / nextEventSequence chain.
     */
    public MoveResult compareAndMove(String deviceId, String newGridId, int newFloorId) {
        final MoveResult[] result = new MoveResult[1];
        states.compute(deviceId, (id, s) -> {
            if (s == null) {
            result[0] = new MoveResult(true, null, null, 1L);
            return new DeviceState(null, newGridId, newFloorId, System.currentTimeMillis(), 1L,
                null, null, null, null, null, 0L);
            }
            boolean gridChanged = newGridId == null ? s.lastGridId() != null : !newGridId.equals(s.lastGridId());
            if (!gridChanged && newFloorId == s.lastFloorId()) {
            result[0] = new MoveResult(false, s.lastGridId(),
                s.lastFloorId() >= 0 ? s.lastFloorId() : null, s.lastEventSequence());
            return s;
            }
            long newSeq = s.lastEventSequence() + 1;
            result[0] = new MoveResult(true, s.lastGridId(),
                s.lastFloorId() >= 0 ? s.lastFloorId() : null, newSeq);
            return new DeviceState(s.session(), newGridId, newFloorId, System.currentTimeMillis(), newSeq,
                s.lastArtId(), s.lastArtTitle(), s.lastArtArtist(), s.lastArtDescription(), s.lastArtSource(), s.lastArtAt());
        });
        return result[0];
    }

    public long nextEventSequence(String deviceId) {
        DeviceState updated = states.compute(deviceId, (id, s) -> {
            if (s == null) {
                return new DeviceState(null, null, -1, System.currentTimeMillis(), 1,
                        null, null, null, null, null, 0L);
            }
            return new DeviceState(
                    s.session(),
                    s.lastGridId(),
                    s.lastFloorId(),
                    System.currentTimeMillis(),
                    s.lastEventSequence() + 1,
                    s.lastArtId(), s.lastArtTitle(), s.lastArtArtist(), s.lastArtDescription(), s.lastArtSource(), s.lastArtAt()
            );
        });
        return updated.lastEventSequence();
    }

    public void touch(String deviceId) {
        states.computeIfPresent(deviceId, (id, s) ->
            new DeviceState(s.session(), s.lastGridId(), s.lastFloorId(), System.currentTimeMillis(), s.lastEventSequence(),
                s.lastArtId(), s.lastArtTitle(), s.lastArtArtist(), s.lastArtDescription(), s.lastArtSource(), s.lastArtAt())
        );
    }

    public List<RemovedDevice> removeInactive(long inactiveTimeoutMs) {
        long now = System.currentTimeMillis();
        List<RemovedDevice> removed = new ArrayList<>();

        states.forEach((deviceId, state) -> {
            if (now - state.lastSeenAt() > inactiveTimeoutMs) {
                DeviceState deleted = states.remove(deviceId);
                if (deleted != null && deleted.lastGridId() != null && deleted.lastFloorId() >= 0) {
                    removed.add(new RemovedDevice(
                            deviceId,
                            deleted.lastGridId(),
                            deleted.lastFloorId(),
                            deleted.lastEventSequence() + 1
                    ));
                }
            }
        });

        return removed;
    }

    public List<AdminDeviceInfo> getDeviceSnapshots() {
        return states.entrySet().stream()
                .map(entry -> new AdminDeviceInfo(
                        entry.getKey(),
                        entry.getValue().session() != null && entry.getValue().session().isOpen() ? "connected" : "offline",
                        entry.getValue().lastGridId(),
                        entry.getValue().lastFloorId(),
                entry.getValue().lastSeenAt(),
                entry.getValue().lastEventSequence(),
                entry.getValue().lastArtId(),
                entry.getValue().lastArtTitle(),
                entry.getValue().lastArtArtist(),
                entry.getValue().lastArtDescription(),
                entry.getValue().lastArtSource(),
                entry.getValue().lastArtAt()
                ))
                .toList();
    }

    public AdminDeviceInfo getDevice(String deviceId) {
        DeviceState s = states.get(deviceId);
        if (s == null) return null;
        return new AdminDeviceInfo(
                deviceId,
                s.session() != null && s.session().isOpen() ? "connected" : "offline",
                s.lastGridId(),
                s.lastFloorId(),
                s.lastSeenAt(),
                s.lastEventSequence(),
                s.lastArtId(),
                s.lastArtTitle(),
                s.lastArtArtist(),
                s.lastArtDescription(),
                s.lastArtSource(),
                s.lastArtAt()
        );
    }

    public void recordLastArt(String deviceId, String artId, String title, String artist, String description, String source) {
        states.compute(deviceId, (id, s) -> {
            if (s == null) {
                return new DeviceState(null, null, -1, System.currentTimeMillis(), 0L,
                        artId, title, artist, description, source, System.currentTimeMillis());
            }
            return new DeviceState(s.session(), s.lastGridId(), s.lastFloorId(), s.lastSeenAt(), s.lastEventSequence(),
                    artId, title, artist, description, source, System.currentTimeMillis());
        });
    }
}