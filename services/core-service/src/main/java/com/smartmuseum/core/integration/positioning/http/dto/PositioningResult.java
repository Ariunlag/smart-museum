package com.smartmuseum.core.integration.positioning.http.dto;

public record PositioningResult(
    String deviceId,
        int    floorId,
        String gridId,
        int    x,
        int    y,
        double confidence
) {
} 