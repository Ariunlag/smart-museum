package com.smartmuseum.core.client.web;

import com.smartmuseum.core.client.web.dto.AckResponse;
import com.smartmuseum.core.client.web.dto.ProximityScanRequest;
import com.smartmuseum.core.orchestration.application.ProximityScanUseCase;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/proximity")
public class ProximityScanController {

    private final ProximityScanUseCase useCase;

    public ProximityScanController(ProximityScanUseCase useCase) {
        this.useCase = useCase;
    }

    /**
     * Receives an art identifier from a QR or NFC scan.
     *
     * POST /proximity/scan
     * {
     *   "deviceId": "phone-123",
     *   "artId":    "abc123",
     *   "source":   "QR"       // "QR" | "NFC" | "MANUAL"
     * }
     */
    @PostMapping("/scan")
    public ResponseEntity<AckResponse> scan(
            @Valid @RequestBody ProximityScanRequest req) {
        useCase.handle(req);
        return ResponseEntity.accepted().body(AckResponse.accepted());
    }
}