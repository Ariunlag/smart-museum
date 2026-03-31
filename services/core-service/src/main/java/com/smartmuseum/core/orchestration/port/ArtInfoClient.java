package com.smartmuseum.core.orchestration.port;

import com.smartmuseum.core.integration.artinfo.http.dto.ArtInfoResult;

import com.smartmuseum.core.integration.artinfo.http.dto.ArtByIdResult;

public interface ArtInfoClient {
    ArtInfoResult findNearest(String gridId, int floorId);
    ArtByIdResult findById(String artId);
}