package com.smartmuseum.core.integration.artinfo.http.dto;

/**
 * Result returned by ArtInfo when searching by artId.
 * Includes gridId and floorId for heatmap and WebSocket flows.
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