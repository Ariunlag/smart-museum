package com.smartmuseum.core.client.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ProximityScanRequest(
        @NotBlank String deviceId,
        @NotBlank String artId,
                  String source    // "QR" | "NFC" | "MANUAL"
) {}