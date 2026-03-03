package com.smartmuseum.positioning.web;

import com.smartmuseum.positioning.service.PositioningService;
import com.smartmuseum.positioning.web.dto.BleReadingsRequest;
import com.smartmuseum.positioning.web.dto.PositioningResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/positioning")
public class PositioningController {

    private final PositioningService service;

    public PositioningController(PositioningService service) {
        this.service = service;
    }

    @PostMapping("/readings")
    public PositioningResponse locate(@Valid @RequestBody BleReadingsRequest req) {
        return service.locate(req);
    }
}