package com.smartmuseum.core.registry;

import com.smartmuseum.core.client.ws.SessionRegistry;
import com.smartmuseum.core.common.config.MuseumProperties;
import com.smartmuseum.core.registry.domain.ServiceRecord;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin-only endpoints for service registry management.
 *
 * GET    /admin/services                      -> list all services
 * GET    /services                            -> list ACTIVE services (public)
 * PATCH  /admin/services/{id}/disable         -> disable a service
 * PATCH  /admin/services/{id}/enable          -> enable a service
 * DELETE /admin/services/{id}                 -> soft delete (REMOVED)
 */
@RestController
public class AdminController {

    private final RegistryService  registryService;
    private final MuseumProperties props;
    private final SessionRegistry  sessionRegistry;

    public AdminController(RegistryService registryService, MuseumProperties props, SessionRegistry sessionRegistry) {
        this.registryService = registryService;
        this.props           = props;
        this.sessionRegistry = sessionRegistry;
    }

    // ── Public ───────────────────────────────────────────
    @GetMapping("/services")
    public List<ServiceRecord> listActive() {
        return registryService.listActive();
    }

    // ── Admin ────────────────────────────────────────────
    @GetMapping("/admin/services")
    public List<ServiceRecord> listAll() {
        return registryService.listAll();
    }

    @GetMapping("/admin/site-config")
    public Map<String, String> siteConfig() {
        String spaceId = props.getSpaceId();
        if (spaceId == null || spaceId.isBlank()) {
            spaceId = "smart-museum";
        }
        Map<String, String> response = new HashMap<>();
        response.put("spaceId", spaceId);
        response.put("site", spaceId.contains("university") ? "smart-university" : "smart-museum");
        return response;
    }

    @PatchMapping("/admin/services/{serviceId}/disable")
    public ResponseEntity<ServiceRecord> disable(
            @PathVariable String serviceId,
            @RequestParam(defaultValue = "admin") String adminId) {
        return ResponseEntity.ok(registryService.disable(serviceId, adminId));
    }

    @PatchMapping("/admin/services/{serviceId}/enable")
    public ResponseEntity<ServiceRecord> enable(@PathVariable String serviceId) {
        return ResponseEntity.ok(registryService.enable(serviceId));
    }

    @DeleteMapping("/admin/services/{serviceId}")
    public ResponseEntity<ServiceRecord> remove(
            @PathVariable String serviceId,
            @RequestParam(defaultValue = "admin") String adminId) {
        return ResponseEntity.ok(registryService.remove(serviceId, adminId));
    }

    // ── Device Monitoring ────────────────────────────────
    @GetMapping("/admin/devices")
    public List<SessionRegistry.AdminDeviceInfo> listDevices() {
        return sessionRegistry.getDeviceSnapshots();
    }

    @GetMapping("/admin/devices/{deviceId}")
    public ResponseEntity<SessionRegistry.AdminDeviceInfo> getDevice(@PathVariable String deviceId) {
        SessionRegistry.AdminDeviceInfo device = sessionRegistry.getDevice(deviceId);
        return device != null ? ResponseEntity.ok(device) : ResponseEntity.notFound().build();
    }
}
