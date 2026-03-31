package com.smartmuseum.core.client.ws;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class SessionRegistryTest {

    @Test
    void addGetRemoveSession_shouldManageSessionLifecycle() {
        SessionRegistry registry = new SessionRegistry();
        WebSocketSession session = mock(WebSocketSession.class);

        registry.addSession("dev-1", session);
        assertSame(session, registry.getSession("dev-1"));

        registry.removeSession("dev-1");
        assertNull(registry.getSession("dev-1"));
    }

    @Test
    void hasLocationChanged_shouldReturnTrueWhenNoState() {
        SessionRegistry registry = new SessionRegistry();
        assertTrue(registry.hasLocationChanged("missing", "A1", 1));
    }

    @Test
    void hasLocationChanged_shouldCompareGridAndFloorNullSafely() {
        SessionRegistry registry = new SessionRegistry();
        WebSocketSession session = mock(WebSocketSession.class);
        registry.addSession("dev-2", session);

        assertTrue(registry.hasLocationChanged("dev-2", "A1", 1));
        registry.updateLocation("dev-2", "A1", 1);
        assertFalse(registry.hasLocationChanged("dev-2", "A1", 1));
        assertTrue(registry.hasLocationChanged("dev-2", null, 1));
        assertTrue(registry.hasLocationChanged("dev-2", "A1", 2));
    }

    @Test
    void updateLocation_shouldTrackDeviceWithoutActiveSession() {
        SessionRegistry registry = new SessionRegistry();

        registry.updateLocation("dev-3", "B2", 2);

        assertFalse(registry.hasLocationChanged("dev-3", "B2", 2));
        assertEquals("B2", registry.getLastGridId("dev-3"));
        assertEquals(2, registry.getLastFloorId("dev-3"));
    }

    @Test
    void removeInactive_shouldReturnDevicesWithLocation() throws InterruptedException {
        SessionRegistry registry = new SessionRegistry();
        registry.updateLocation("dev-4", "C3", 3);

        Thread.sleep(5);
        List<SessionRegistry.RemovedDevice> removed = registry.removeInactive(1);

        assertEquals(1, removed.size());
        assertEquals("dev-4", removed.get(0).deviceId());
        assertEquals("C3", removed.get(0).lastGridId());
        assertEquals(3, removed.get(0).lastFloorId());
    }
}
