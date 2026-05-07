package com.smartmuseum.core.orchestration.application;

import com.smartmuseum.core.client.web.dto.ProximityScanRequest;
import com.smartmuseum.core.client.ws.DeviceWebSocketHandler;
import com.smartmuseum.core.client.ws.SessionRegistry;
import com.smartmuseum.core.client.ws.dto.PushMessage;
import com.smartmuseum.core.integration.artinfo.http.dto.ArtByIdResult;
import com.smartmuseum.core.integration.mqtt.HeatmapPublisher;
import com.smartmuseum.core.orchestration.port.ArtInfoClient;
import com.smartmuseum.core.registry.RegistryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ProximityScanUseCaseTest {

    private ArtInfoClient artInfoClient;
    private HeatmapPublisher heatmapPublisher;
    private DeviceWebSocketHandler wsHandler;
    private SessionRegistry sessionRegistry;
    private RegistryService registryService;
    private ProximityScanUseCase useCase;

    @BeforeEach
    void setUp() {
        artInfoClient = mock(ArtInfoClient.class);
        heatmapPublisher = mock(HeatmapPublisher.class);
        wsHandler = mock(DeviceWebSocketHandler.class);
        sessionRegistry = mock(SessionRegistry.class);
        registryService = mock(RegistryService.class);

        useCase = new ProximityScanUseCase(
                artInfoClient,
                heatmapPublisher,
                wsHandler,
                sessionRegistry,
                registryService
        );
    }

    @Test
    void handle_shouldPushServiceUnavailableWhenArtInfoInactive() {
        when(registryService.isServiceActive("artinfo-service")).thenReturn(false);

        useCase.handle(new ProximityScanRequest("dev-1", "art-1", "QR"));

        ArgumentCaptor<PushMessage> captor = ArgumentCaptor.forClass(PushMessage.class);
        verify(wsHandler).push(eq("dev-1"), captor.capture());
        assertEquals("service_unavailable", captor.getValue().type());
        verifyNoInteractions(artInfoClient);
    }

    @Test
    void handle_shouldSkipWhenArtLookupThrows() {
        when(registryService.isServiceActive("artinfo-service")).thenReturn(true);
        when(artInfoClient.findById("art-1")).thenThrow(new RuntimeException("down"));

        useCase.handle(new ProximityScanRequest("dev-1", "art-1", "NFC"));

        verify(wsHandler, never()).push(eq("dev-1"), any());
        verifyNoInteractions(heatmapPublisher);
    }

    @Test
    void handle_shouldPushAndPublishWhenLocationChanged() {
        when(registryService.isServiceActive("artinfo-service")).thenReturn(true);
        when(artInfoClient.findById("art-1")).thenReturn(
                new ArtByIdResult("art-1", "Title", "Artist", "Desc", "B3", 2, 2, 1)
        );
        when(sessionRegistry.compareAndMove("dev-1", "B3", 2)).thenReturn(
                new SessionRegistry.MoveResult(true, "B2", 1, 3L));

        useCase.handle(new ProximityScanRequest("dev-1", "art-1", "QR"));

        verify(wsHandler).push(eq("dev-1"), any(PushMessage.class));
        verify(heatmapPublisher).publish("dev-1", 3L, "B3", 2, "B2", 1);
    }
}
