package com.smartmuseum.positioning.floor;

import com.smartmuseum.positioning.web.dto.BleReadingsRequest;

/**
 * Strategy interface for floor detection.
 * Current implementation: beacon; can be extended with manual, QR, etc.
 */
public interface FloorDetectionStrategy {
    int detect(BleReadingsRequest req);
}