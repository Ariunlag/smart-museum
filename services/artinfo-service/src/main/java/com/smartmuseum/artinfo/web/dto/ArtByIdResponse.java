package com.smartmuseum.artinfo.web.dto;

public record ArtByIdResponse(
        String artId,
        String title,
        String artist,
        String description,
        String gridId,
        int floorId,
        int x,
        int y
) {}
