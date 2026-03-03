package com.smartmuseum.core.client.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record BleIngestRequest(
        @JsonProperty("deviceId")  @NotBlank  String deviceId,
        @JsonProperty("timestamp") @NotNull   Long   timestamp,
        @JsonProperty("readings")  @NotEmpty  List<@Valid BleReadingDto> readings
) {
    public record BleReadingDto(
            @JsonProperty("beaconId") @NotBlank String  beaconId,
            @JsonProperty("rssi")     @NotNull  Integer rssi
    ) {}
}