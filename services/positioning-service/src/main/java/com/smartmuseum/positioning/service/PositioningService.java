package com.smartmuseum.positioning.service;

import com.smartmuseum.positioning.floor.FloorDetectionFactory;
import com.smartmuseum.positioning.qdrant.QdrantRepository;
import com.smartmuseum.positioning.vector.VectorBuilder;
import com.smartmuseum.positioning.web.dto.BleReadingsRequest;
import com.smartmuseum.positioning.web.dto.PositioningResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PositioningService {

    private static final Logger log = LoggerFactory.getLogger(PositioningService.class);

    private final FloorDetectionFactory floorFactory;
    private final VectorBuilder         vectorBuilder;
    private final QdrantRepository      qdrant;

    public PositioningService(FloorDetectionFactory floorFactory,
                              VectorBuilder vectorBuilder,
                              QdrantRepository qdrant) {
        this.floorFactory  = floorFactory;
        this.vectorBuilder = vectorBuilder;
        this.qdrant        = qdrant;
    }

    public PositioningResponse locate(BleReadingsRequest req) {
        // 1. Давхар тодорхойлно
        int floorId = floorFactory.get().detect(req);
        log.info("Floor detected: {}", floorId);

        // 2. Vector үүсгэнэ
        var vector = vectorBuilder.build(req);

        // 3. Qdrant ANN search → gridId + confidence score
        var searchResult = qdrant.searchNearest(vector, floorId);
        if (searchResult == null) {
            log.warn("No grid found for device: {}", req.deviceId());
            return new PositioningResponse(req.deviceId(), floorId, "UNKNOWN", 0, 0, 0.0);
        }

        String gridId    = searchResult.gridId();
        double confidence = searchResult.score();
        log.info("Grid found: {} score: {}", gridId, confidence);

        // 4. GridId-с x, y тооцоолно ("B3" → x=2, y=1)
        int x = gridId.length() > 1 ? Integer.parseInt(gridId.substring(1)) - 1 : 0;
        int y = gridId.length() > 0 ? gridId.charAt(0) - 'A' : 0;

        return new PositioningResponse(
                req.deviceId(),
                floorId,
                gridId,
                x,
                y,
                confidence  // ← Qdrant-с авсан score
        );
    }
}