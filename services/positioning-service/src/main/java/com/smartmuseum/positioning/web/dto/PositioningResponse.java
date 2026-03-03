package com.smartmuseum.positioning.web.dto;

public record PositioningResponse(
        String deviceId,
        int    floorId,
        String gridId,
        int    x,
        int    y,
        double confidence
) {}