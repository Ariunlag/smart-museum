package com.smartmuseum.core.orchestration.port;

import com.smartmuseum.core.client.web.dto.BleIngestRequest;
import com.smartmuseum.core.integration.positioning.http.dto.PositioningResult;


public interface PositioningClient {
    PositioningResult locate(BleIngestRequest req);
}
