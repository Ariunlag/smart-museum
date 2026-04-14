package com.smartmuseum.core.registry;

import com.smartmuseum.core.common.config.MuseumProperties;
import com.smartmuseum.core.registry.domain.ServiceRecord;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.HashMap;
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
import org.springframework.web.bind.annotation.CrossOrigin;

@CrossOrigin(origins = "*")
@RestController
public class AdminController {

    private final RegistryService  registryService;
    private final MuseumProperties props;

    public AdminController(RegistryService registryService, MuseumProperties props) {
        this.registryService = registryService;
        this.props           = props;
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
}