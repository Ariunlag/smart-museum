package com.smartmuseum.artinfo.web;

import com.smartmuseum.artinfo.service.ArtInfoService;
import com.smartmuseum.artinfo.web.dto.ArtInfoResponse;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/art")
public class ArtInfoController {

    private final ArtInfoService service;

    public ArtInfoController(ArtInfoService service) {
        this.service = service;
    }

    @GetMapping("/nearest")
    public ArtInfoResponse nearest(
            @RequestParam @NotBlank String gridId,
            @RequestParam int floorId) {
        return service.findNearest(gridId, floorId);
    }
}