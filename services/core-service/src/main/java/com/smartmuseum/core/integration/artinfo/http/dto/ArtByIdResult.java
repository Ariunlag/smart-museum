package com.smartmuseum.core.integration.artinfo.http.dto;

/**
 * ArtInfo service-с artId-р хайсан үр дүн.
 * gridId, floorId-г агуулна — heatmap + WebSocket-д ашиглана.
 */
public record ArtByIdResult(
        String artId,
        String title,
        String artist,
        String description,
        String gridId,
        int    floorId,
        int    x,
        int    y
) {}