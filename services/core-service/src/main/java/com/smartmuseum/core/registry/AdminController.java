package com.smartmuseum.core.registry;

import com.smartmuseum.core.registry.domain.ServiceRecord;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin-only endpoints (MD файлын Admin-Only Actions).
 *
 * GET    /admin/services                      → бүгдийг харна
 * GET    /services                            → ACTIVE-г харна (public)
 * PATCH  /admin/services/{id}/disable         → disable хийнэ
 * PATCH  /admin/services/{id}/enable          → enable хийнэ
 * DELETE /admin/services/{id}                 → soft delete (REMOVED)
 */
import org.springframework.web.bind.annotation.CrossOrigin;

@CrossOrigin(origins = "*")
@RestController
public class AdminController {

    private final RegistryService registryService;

    public AdminController(RegistryService registryService) {
        this.registryService = registryService;
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