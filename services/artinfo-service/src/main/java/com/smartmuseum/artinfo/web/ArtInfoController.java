package com.smartmuseum.artinfo.web;

import com.smartmuseum.artinfo.service.ArtInfoService;
import com.smartmuseum.artinfo.web.dto.ArtByIdResponse;
import com.smartmuseum.artinfo.web.dto.ArtInfoResponse;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/internal/art")
@CrossOrigin(origins = "*")
public class ArtInfoController {

    private final ArtInfoService service;

    public ArtInfoController(ArtInfoService service) {
        this.service = service;
    }

    // Nearest arts by gridId + floorId (BLE flow)
    @GetMapping("/nearest")
    public ArtInfoResponse nearest(
            @RequestParam @NotBlank String gridId,
            @RequestParam int floorId) {
        return service.findNearest(gridId, floorId);
    }

    // Single art by id (QR/NFC flow)
    @GetMapping("/{artId}")
    public ArtByIdResponse byId(@PathVariable String artId) {
        return service.findByIdWithLocation(artId);
    }

    // List of all art IDs (used by the simulator)
    @GetMapping
    public List<ArtInfoResponse.ArtDto> listAll() {
        return service.findAll();
    }

    // Admin: Art statistics
    @GetMapping("/admin/system/art-stats")
    public Map<String, Object> artStatistics() {
        return service.getArtStatistics();
    }

    // Admin: vector build and Qdrant status
    @GetMapping("/admin/system/vector-stats")
    public Map<String, Object> vectorStatistics() {
        return service.getVectorStatistics();
    }
}