package com.smartmuseum.artinfo.web;

import com.smartmuseum.artinfo.service.ArtInfoService;
import com.smartmuseum.artinfo.web.dto.ArtByIdResponse;
import com.smartmuseum.artinfo.web.dto.ArtInfoResponse;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/internal/art")
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

    // Бүх art-н ID жагсаалт (simulator-д ашиглана)
    @GetMapping
    public List<ArtInfoResponse.ArtDto> listAll() {
        return service.findAll();
    }
}