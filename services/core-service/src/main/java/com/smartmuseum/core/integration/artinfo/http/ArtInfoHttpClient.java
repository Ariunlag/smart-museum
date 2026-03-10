package com.smartmuseum.core.integration.artinfo.http;

import com.smartmuseum.core.common.config.MuseumProperties;
import com.smartmuseum.core.integration.artinfo.http.dto.ArtInfoResult;
import com.smartmuseum.core.orchestration.port.ArtInfoClient;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Component
public class ArtInfoHttpClient implements ArtInfoClient {

    private final WebClient        webClient;
    private final MuseumProperties props;

    public ArtInfoHttpClient(WebClient webClient, MuseumProperties props) {
        this.webClient = webClient;
        this.props     = props;
    }

    @Override
    public ArtInfoResult findNearest(String gridId, int floorId) {
        var a = props.getServices().getArtinfo();
        return webClient.get()
                .uri(a.getBaseUrl() + a.getEndpoint(),
                        b -> b.queryParam("gridId", gridId)
                              .queryParam("floorId", floorId)
                              .build())
                .retrieve()
                .bodyToMono(ArtInfoResult.class)
                .timeout(Duration.ofMillis(a.getTimeoutMs()))
                .block();
    }
}