package com.smartmuseum.core.registry;

import com.smartmuseum.core.common.config.MuseumProperties;
import com.smartmuseum.core.registry.domain.ServiceRecord;
import com.smartmuseum.core.registry.domain.ServiceRecord.Status;
import com.smartmuseum.core.registry.domain.ServiceRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class RegistryService {

    private static final Logger log = LoggerFactory.getLogger(RegistryService.class);

    private final ServiceRecordRepository repo;
    private final MuseumProperties        props;

    public RegistryService(ServiceRecordRepository repo, MuseumProperties props) {
        this.repo  = repo;
        this.props = props;
    }

    // ── MQTT Register event ──────────────────────────────
    public void onRegister(Map<String, Object> payload) {
        String serviceId   = (String) payload.get("serviceId");
        String serviceName = (String) payload.get("serviceName");
        String serviceUrl  = (String) payload.get("serviceUrl");
        String version     = (String) payload.getOrDefault("version", "1.0.0");

        if (serviceId == null || serviceName == null) return;

        Optional<ServiceRecord> existing = repo.findByServiceId(serviceId);

        if (existing.isPresent()) {
            ServiceRecord rec = existing.get();
            // REMOVED service-г admin л дахин идэвхжүүлнэ
            if (rec.getStatus() == Status.REMOVED) {
                log.warn("Registry: REMOVED service tried to register: {}", serviceId);
                return;
            }
            // DISABLED бол heartbeat шинэчилнэ, ACTIVE болгохгүй
            if (rec.getStatus() != Status.DISABLED) {
                rec.setStatus(Status.ACTIVE);
            }
            rec.setServiceUrl(serviceUrl);
            rec.setVersion(version);
            rec.setLastHeartbeatAt(System.currentTimeMillis());
            rec.setLastUpdatedAt(System.currentTimeMillis());
            repo.save(rec);
            log.info("Registry: updated service {}", serviceId);
        } else {
            var rec = new ServiceRecord(serviceId, serviceName, serviceUrl, version);
            repo.save(rec);
            log.info("Registry: registered new service {}", serviceId);
        }
    }

    // ── MQTT Heartbeat event ─────────────────────────────
    public void onHeartbeat(Map<String, Object> payload) {
        String serviceId = (String) payload.get("serviceId");
        if (serviceId == null) return;

        repo.findByServiceId(serviceId).ifPresent(rec -> {
            // DISABLED/REMOVED бол auto-activate хийхгүй
            if (rec.getStatus() == Status.DISABLED ||
                rec.getStatus() == Status.REMOVED) return;

            rec.setLastHeartbeatAt(System.currentTimeMillis());
            if (rec.getStatus() == Status.OFFLINE) {
                rec.setStatus(Status.ACTIVE);
                log.info("Registry: {} came back ONLINE", serviceId);
            }
            repo.save(rec);
        });
    }

    // ── Offline detection (scheduled) ───────────────────
    @Scheduled(fixedDelayString = "${museum.registry.check-interval-ms}")
    public void checkOfflineServices() {
        long timeout = props.getRegistry().getHeartbeatTimeoutMs();
        long now     = System.currentTimeMillis();

        repo.findByStatus(Status.ACTIVE).forEach(rec -> {
            if (now - rec.getLastHeartbeatAt() > timeout) {
                rec.setStatus(Status.OFFLINE);
                rec.setLastUpdatedAt(now);
                repo.save(rec);
                log.warn("Registry: {} went OFFLINE", rec.getServiceId());
            }
        });
    }
    public boolean isServiceActive(String serviceId) {
        return repo.findByServiceId(serviceId)
                .map(s -> s.getStatus() == Status.ACTIVE)
                .orElse(true);
    }
    // ── Admin actions ────────────────────────────────────
    public List<ServiceRecord> listAll() {
        return repo.findAll();
    }

    public List<ServiceRecord> listActive() {
        return repo.findByStatus(Status.ACTIVE);
    }

    public ServiceRecord disable(String serviceId, String adminId) {
        return repo.findByServiceId(serviceId).map(rec -> {
            rec.setStatus(Status.DISABLED);
            rec.setDisabledBy(adminId);
            rec.setLastUpdatedAt(System.currentTimeMillis());
            return repo.save(rec);
        }).orElseThrow(() -> new RuntimeException("Service not found: " + serviceId));
    }

    public ServiceRecord enable(String serviceId) {
        return repo.findByServiceId(serviceId).map(rec -> {
            rec.setStatus(Status.ACTIVE);
            rec.setDisabledBy(null);
            rec.setLastUpdatedAt(System.currentTimeMillis());
            return repo.save(rec);
        }).orElseThrow(() -> new RuntimeException("Service not found: " + serviceId));
    }

    public ServiceRecord remove(String serviceId, String adminId) {
        return repo.findByServiceId(serviceId).map(rec -> {
            rec.setStatus(Status.REMOVED);
            rec.setRemovedBy(adminId);
            rec.setLastUpdatedAt(System.currentTimeMillis());
            return repo.save(rec);
        }).orElseThrow(() -> new RuntimeException("Service not found: " + serviceId));
    }
}