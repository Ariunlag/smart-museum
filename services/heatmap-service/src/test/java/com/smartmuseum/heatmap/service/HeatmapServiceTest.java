package com.smartmuseum.heatmap.service;

import com.smartmuseum.heatmap.config.HeatmapProperties;
import com.smartmuseum.heatmap.store.GridCount;
import com.smartmuseum.heatmap.store.GridCountRepository;
import com.smartmuseum.heatmap.store.HeatmapStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class HeatmapServiceTest {

    private HeatmapStore store;
    private GridCountRepository repository;
    private HeatmapService service;

    @BeforeEach
    void setUp() {
        store = mock(HeatmapStore.class);
        repository = mock(GridCountRepository.class);

        HeatmapProperties props = new HeatmapProperties();
        props.getHeatmap().setCrowdThreshold(3);

        service = new HeatmapService(store, repository, props);
    }

    @Test
    void processEvent_shouldLeavePreviousAndEnterCurrent() {
        when(store.getCount("A1", 1)).thenReturn(2);

        service.processEvent("A1", 1, "A0", 3);

        verify(store).leave("A0", 3);
        verify(store).enter("A1", 1);
    }

    @Test
    void processLeave_shouldIgnoreNullGrid() {
        service.processLeave(null, 1);
        verify(store, never()).leave(any(), anyInt());
    }

    @Test
    void persist_shouldCreateOrUpdateGridCountDocuments() {
        GridCount existing = new GridCount("A1", 1);

        when(store.snapshot()).thenReturn(Map.of("1:A1", 5, "2:B2", 3));
        when(repository.findByGridIdAndFloorId("A1", 1)).thenReturn(Optional.of(existing));
        when(repository.findByGridIdAndFloorId("B2", 2)).thenReturn(Optional.empty());

        service.persist();

        verify(repository).findByGridIdAndFloorId("A1", 1);
        verify(repository).findByGridIdAndFloorId("B2", 2);
        verify(repository, times(2)).save(any(GridCount.class));
        verify(repository).save(eq(existing));
    }

    @Test
    void persist_shouldDeleteExistingRecordWhenCountIsZero() {
        GridCount existing = new GridCount("A1", 1);
        when(store.snapshot()).thenReturn(Map.of("1:A1", 0));
        when(repository.findByGridIdAndFloorId("A1", 1)).thenReturn(Optional.of(existing));

        service.persist();

        verify(repository).delete(existing);
        verify(repository, never()).save(any(GridCount.class));
    }
}
