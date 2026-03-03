package com.smartmuseum.positioning.floor;

import com.smartmuseum.positioning.web.dto.BleReadingsRequest;

/**
 * Давхар тодорхойлох strategy interface.
 * Одоо: beacon → цаашид: manual, qr гэх мэт нэмэх боломжтой.
 */
public interface FloorDetectionStrategy {
    int detect(BleReadingsRequest req);
}