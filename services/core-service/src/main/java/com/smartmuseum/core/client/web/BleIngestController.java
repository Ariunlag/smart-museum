package com.smartmuseum.core.client.web;

import com.smartmuseum.core.client.web.dto.AckResponse;
import com.smartmuseum.core.client.web.dto.BleIngestRequest;
import com.smartmuseum.core.orchestration.application.ProcessBleUseCase;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ble")
public class BleIngestController {

    private final ProcessBleUseCase useCase;

    public BleIngestController(ProcessBleUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping("/readings")
    public ResponseEntity<AckResponse> postReadings(
            @Valid @RequestBody BleIngestRequest req) {
        useCase.handle(req);
        return ResponseEntity.accepted().body(AckResponse.accepted());
    }
}