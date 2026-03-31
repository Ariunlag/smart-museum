package com.smartmuseum.positioning.service;

import com.smartmuseum.positioning.floor.FloorDetectionFactory;
import com.smartmuseum.positioning.floor.FloorDetectionStrategy;
import com.smartmuseum.positioning.qdrant.QdrantRepository;
import com.smartmuseum.positioning.vector.VectorBuilder;
import com.smartmuseum.positioning.web.dto.BleReadingsRequest;
import com.smartmuseum.positioning.web.dto.PositioningResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

class PositioningServiceTest {

    private FloorDetectionFactory floorFactory;
    private FloorDetectionStrategy floorStrategy;
    private VectorBuilder vectorBuilder;
    private QdrantRepository qdrant;
    private PositioningService service;

    @BeforeEach
    void setUp() {
        floorFactory = mock(FloorDetectionFactory.class);
        floorStrategy = mock(FloorDetectionStrategy.class);
        vectorBuilder = mock(VectorBuilder.class);
        qdrant = mock(QdrantRepository.class);

        service = new PositioningService(floorFactory, vectorBuilder, qdrant);
    }

    @Test
    void locate_shouldReturnUnknownWhenSearchHasNoResult() {
        BleReadingsRequest req = sampleRequest();
        when(floorFactory.get()).thenReturn(floorStrategy);
        when(floorStrategy.detect(req)).thenReturn(2);
        when(vectorBuilder.build(req)).thenReturn(List.of(0.2f, 0.8f));
        when(qdrant.searchNearest(anyList(), eq(2))).thenReturn(null);

        PositioningResponse res = service.locate(req);

        assertEquals("UNKNOWN", res.gridId());
        assertEquals(0, res.x());
        assertEquals(0, res.y());
        assertEquals(0.0, res.confidence());
    }

    @Test
    void locate_shouldMapQdrantResultToCoordinates() {
        BleReadingsRequest req = sampleRequest();
        when(floorFactory.get()).thenReturn(floorStrategy);
        when(floorStrategy.detect(req)).thenReturn(3);
        when(vectorBuilder.build(req)).thenReturn(List.of(0.1f, 0.9f));
        when(qdrant.searchNearest(anyList(), eq(3)))
                .thenReturn(new QdrantRepository.SearchResult("B3", 0.87));

        PositioningResponse res = service.locate(req);

        assertNotNull(res);
        assertEquals("dev-1", res.deviceId());
        assertEquals(3, res.floorId());
        assertEquals("B3", res.gridId());
        assertEquals(2, res.x());
        assertEquals(1, res.y());
        assertEquals(0.87, res.confidence());
    }

    private BleReadingsRequest sampleRequest() {
        return new BleReadingsRequest(
                "dev-1",
                10L,
                List.of(new BleReadingsRequest.BleReadingDto("beacon-1", -70))
        );
    }
}
