package com.smartmuseum.core.orchestration.application;

import com.smartmuseum.core.client.web.dto.BleIngestRequest;
import com.smartmuseum.core.client.ws.DeviceWebSocketHandler;
import com.smartmuseum.core.client.ws.SessionRegistry;
import com.smartmuseum.core.client.ws.dto.PushMessage;
import com.smartmuseum.core.common.config.MuseumProperties;
import com.smartmuseum.core.integration.artinfo.http.dto.ArtInfoResult;
import com.smartmuseum.core.integration.mqtt.HeatmapPublisher;
import com.smartmuseum.core.integration.positioning.http.dto.PositioningResult;
import com.smartmuseum.core.orchestration.port.ArtInfoClient;
import com.smartmuseum.core.orchestration.port.PositioningClient;
import com.smartmuseum.core.registry.RegistryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ProcessBleUseCaseTest {

    private PositioningClient positioningClient;
    private ArtInfoClient artInfoClient;
    private HeatmapPublisher heatmapPublisher;
    private DeviceWebSocketHandler wsHandler;
    private SessionRegistry sessionRegistry;
    private RegistryService registryService;
    private MuseumProperties props;
    private ProcessBleUseCase useCase;

    @BeforeEach
    void setUp() {
        positioningClient = mock(PositioningClient.class);
        artInfoClient = mock(ArtInfoClient.class);
        heatmapPublisher = mock(HeatmapPublisher.class);
        wsHandler = mock(DeviceWebSocketHandler.class);
        sessionRegistry = mock(SessionRegistry.class);
        registryService = mock(RegistryService.class);

        props = new MuseumProperties();
        useCase = new ProcessBleUseCase(
                positioningClient,
                artInfoClient,
                heatmapPublisher,
                wsHandler,
                sessionRegistry,
                props,
                registryService
        );
    }

    @Test
    void handle_shouldReturnEarlyWhenPositioningDisabled() {
        props.getServices().getPositioning().setEnabled(false);

        useCase.handle(sampleRequest());

        verifyNoInteractions(positioningClient);
        verifyNoInteractions(wsHandler);
    }

    @Test
    void handle_shouldPushWithEmptyArtsWhenArtinfoDisabled() {
        props.getServices().getPositioning().setEnabled(true);
        props.getServices().getArtinfo().setEnabled(false);
        props.getServices().getHeatmap().setEnabled(false);

        when(registryService.isServiceActive("positioning-service")).thenReturn(true);
        when(positioningClient.locate(any())).thenReturn(
                new PositioningResult("dev-1", 2, "B3", 2, 1, 0.9)
        );

        useCase.handle(sampleRequest());

        verifyNoInteractions(artInfoClient);
        ArgumentCaptor<PushMessage> captor = ArgumentCaptor.forClass(PushMessage.class);
        verify(wsHandler).push(eq("dev-1"), captor.capture());
        assertEquals("location_update", captor.getValue().type());
        assertTrue(captor.getValue().payload().toString().contains("arts=[]"));
    }

    @Test
    void handle_shouldPublishHeatmapWhenLocationChanged() {
        props.getServices().getPositioning().setEnabled(true);
        props.getServices().getArtinfo().setEnabled(true);
        props.getServices().getHeatmap().setEnabled(true);

        when(registryService.isServiceActive("positioning-service")).thenReturn(true);
        when(registryService.isServiceActive("artinfo-service")).thenReturn(true);
        when(registryService.isServiceActive("heatmap-service")).thenReturn(true);

        when(positioningClient.locate(any())).thenReturn(
                new PositioningResult("dev-1", 1, "A1", 0, 0, 0.8)
        );
        when(artInfoClient.findNearest("A1", 1)).thenReturn(new ArtInfoResult(List.of()));
        when(sessionRegistry.hasLocationChanged("dev-1", "A1", 1)).thenReturn(true);
        when(sessionRegistry.getLastGridId("dev-1")).thenReturn("A0");
        when(sessionRegistry.getLastFloorId("dev-1")).thenReturn(2);

        useCase.handle(sampleRequest());

        verify(heatmapPublisher).publish("A1", 1, "A0", 2);
        verify(sessionRegistry).updateLocation("dev-1", "A1", 1);
    }

    private BleIngestRequest sampleRequest() {
        return new BleIngestRequest(
                "dev-1",
                100L,
                List.of(new BleIngestRequest.BleReadingDto("beacon-1", -75))
        );
    }
}
