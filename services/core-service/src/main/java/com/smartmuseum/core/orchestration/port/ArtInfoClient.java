package com.smartmuseum.core.orchestration.port;
import com.smartmuseum.core.integration.artinfo.http.dto.ArtInfoResult;

public interface ArtInfoClient {
    ArtInfoResult findNearest(String gridId, int floorId);

}
