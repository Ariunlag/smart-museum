package com.smartmuseum.artinfo.web.dto;

import java.util.List;

public record ArtInfoResponse(
        List<ArtDto> arts
) {
    public record ArtDto(
            String artId,
            String title,
            String artist,
            String description
    ) {}
}