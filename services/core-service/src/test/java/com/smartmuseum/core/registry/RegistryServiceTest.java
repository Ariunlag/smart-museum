package com.smartmuseum.core.registry;

import com.smartmuseum.core.common.config.MuseumProperties;
import com.smartmuseum.core.registry.domain.ServiceRecord;
import com.smartmuseum.core.registry.domain.ServiceRecord.Status;
import com.smartmuseum.core.registry.domain.ServiceRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RegistryServiceTest {

    private ServiceRecordRepository repo;
    private RegistryService service;

    @BeforeEach
    void setUp() {
        repo = mock(ServiceRecordRepository.class);
        MuseumProperties props = new MuseumProperties();
        props.getRegistry().setHeartbeatTimeoutMs(1);
        service = new RegistryService(repo, props);
    }

    @Test
    void onRegister_shouldSaveNewServiceWhenNotExisting() {
        when(repo.findByServiceId("positioning-service")).thenReturn(Optional.empty());

        service.onRegister(Map.of(
                "serviceId", "positioning-service",
                "serviceName", "Positioning Service",
                "serviceUrl", "http://localhost:8081"
        ));

        verify(repo).save(any(ServiceRecord.class));
    }

    @Test
    void onRegister_shouldIgnoreRemovedService() {
        ServiceRecord removed = record("artinfo-service", Status.REMOVED);
        when(repo.findByServiceId("artinfo-service")).thenReturn(Optional.of(removed));

        service.onRegister(Map.of(
                "serviceId", "artinfo-service",
                "serviceName", "ArtInfo",
                "serviceUrl", "http://localhost:8082"
        ));

        verify(repo, never()).save(any(ServiceRecord.class));
    }

    @Test
    void onHeartbeat_shouldBringOfflineServiceBackToActive() {
        ServiceRecord offline = record("heatmap-service", Status.OFFLINE);
        when(repo.findByServiceId("heatmap-service")).thenReturn(Optional.of(offline));

        service.onHeartbeat(Map.of("serviceId", "heatmap-service"));

        assertEquals(Status.ACTIVE, offline.getStatus());
        verify(repo).save(offline);
    }

    @Test
    void checkOfflineServices_shouldMarkStaleActiveServicesOffline() {
        ServiceRecord active = record("core", Status.ACTIVE);
        active.setLastHeartbeatAt(0L);
        when(repo.findByStatus(Status.ACTIVE)).thenReturn(List.of(active));

        service.checkOfflineServices();

        assertEquals(Status.OFFLINE, active.getStatus());
        verify(repo).save(active);
    }

    @Test
    void isServiceActive_shouldReturnTrueWhenMissing() {
        when(repo.findByServiceId("unknown")).thenReturn(Optional.empty());
        assertTrue(service.isServiceActive("unknown"));
    }

    @Test
    void disableEnableRemove_shouldUpdateStatusAndAuditFields() {
        ServiceRecord record = record("s1", Status.ACTIVE);
        when(repo.findByServiceId("s1")).thenReturn(Optional.of(record));
        when(repo.save(any(ServiceRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        ServiceRecord disabled = service.disable("s1", "admin");
        assertEquals(Status.DISABLED, disabled.getStatus());
        assertEquals("admin", disabled.getDisabledBy());

        ServiceRecord enabled = service.enable("s1");
        assertEquals(Status.ACTIVE, enabled.getStatus());
        assertNull(enabled.getDisabledBy());

        ServiceRecord removed = service.remove("s1", "admin2");
        assertEquals(Status.REMOVED, removed.getStatus());
        assertEquals("admin2", removed.getRemovedBy());
    }

    private ServiceRecord record(String serviceId, Status status) {
        ServiceRecord rec = new ServiceRecord(serviceId, serviceId, "http://x", "1.0.0");
        rec.setStatus(status);
        return rec;
    }
}
