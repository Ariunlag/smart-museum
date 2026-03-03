
package com.smartmuseum.core.integration.artinfo.http.dto;

import java.util.List;

public record ArtInfoResult(
        List<ArtDto> arts
) {
    public record ArtDto(
            String artId,
            String title,
            String artist,
            String description
    ) {}
}