package com.smartmuseum.positioning.qdrant;

import com.smartmuseum.positioning.config.QdrantProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Repository
public class QdrantRepository {

    private static final Logger log = LoggerFactory.getLogger(QdrantRepository.class);

    private final WebClient        qdrantClient;
    private final QdrantProperties props;

    public QdrantRepository(@Qualifier("qdrantWebClient") WebClient qdrantClient,
                            QdrantProperties props) {
        this.qdrantClient = qdrantClient;
        this.props        = props;
    }

    public void ensureCollection(int vectorSize) {
        var exists = qdrantClient.get()
                .uri("/collections/{name}", props.getCollection())
                .retrieve()
                .bodyToMono(Map.class)
                .onErrorReturn(Map.of())
                .block();

        if (exists != null && !exists.isEmpty()) {
            log.info("Collection already exists: {}", props.getCollection());
            return;
        }

        qdrantClient.put()
                .uri("/collections/{name}", props.getCollection())
                .bodyValue(Map.of(
                        "vectors", Map.of(
                                "size", vectorSize,
                                "distance", "Cosine"
                        )
                ))
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        log.info("Collection created: {}", props.getCollection());
    }

    public void upsert(List<Map<String, Object>> points) {
        qdrantClient.put()
                .uri("/collections/{name}/points", props.getCollection())
                .bodyValue(Map.of("points", points))
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        log.info("Upserted {} points", points.size());
    }

    // gridId + score хоёуланг буцаана
    public record SearchResult(String gridId, double score) {}

    @SuppressWarnings("unchecked")
    public SearchResult searchNearest(List<Float> vector, int floorId) {
        var body = Map.of(
                "vector", vector,
                "limit", 1,
                "with_payload", true,
                "filter", Map.of(
                        "must", List.of(
                                Map.of("key", "floorId",
                                        "match", Map.of("value", floorId))
                        )
                )
        );

        var result = qdrantClient.post()
                .uri("/collections/{name}/points/search", props.getCollection())
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (result == null) return null;
        var resultList = (List<Map<String, Object>>) result.get("result");
        if (resultList == null || resultList.isEmpty()) return null;

        var top        = resultList.get(0);
        var payload    = (Map<String, Object>) top.get("payload");
        String gridId  = payload != null ? (String) payload.get("gridId") : null;
        double score   = top.get("score") != null
                ? ((Number) top.get("score")).doubleValue() : 0.0;

        return new SearchResult(gridId, score);
    }
}