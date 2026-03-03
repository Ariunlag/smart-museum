package com.smartmuseum.core.integration.positioning.http;


import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.smartmuseum.core.client.web.dto.BleIngestRequest;
import com.smartmuseum.core.common.config.MuseumProperties;
import com.smartmuseum.core.integration.positioning.http.dto.PositioningResult;
import com.smartmuseum.core.orchestration.port.PositioningClient;

import java.time.Duration;

@Component
public class PositioningHttpClient implements PositioningClient {
    private final WebClient webClient;
    private final MuseumProperties props;

    public PositioningHttpClient(WebClient webClient, MuseumProperties props) {
        this.webClient = webClient;
        this.props     = props;
    }

    @Override
    public PositioningResult locate(BleIngestRequest req) {
        var p = props.getPositioning();
        return webClient.post()
                .uri(p.getBaseUrl() + p.getEndpoint())
                .bodyValue(req)
                .retrieve()
                .bodyToMono(PositioningResult.class)
                .timeout(Duration.ofMillis(p.getTimeoutMs()))
                .block();
    }
}
