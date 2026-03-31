package com.smartmuseum.heatmap.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmuseum.heatmap.service.HeatmapService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Core-с ирсэн MQTT event боловсруулна.
 *
 * Expected payload:
 * {
 *   "gridId":    "C3",
 *   "floorId":   1,
 *   "prevGridId": "B2",   // null бол анхны орох
 *   "timestamp": 123456
 * }
 */
@Component
public class HeatmapSubscriber {

    private static final Logger log = LoggerFactory.getLogger(HeatmapSubscriber.class);

    private final HeatmapService service;
    private final ObjectMapper   mapper;

    public HeatmapSubscriber(HeatmapService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper  = mapper;
    }

    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void handle(Message<String> message) {
        try {
            var payload = mapper.readValue(message.getPayload(), Map.class);

            String gridId    = (String) payload.get("gridId");
            int    floorId   = ((Number) payload.get("floorId")).intValue();
            String prevGridId = (String) payload.get("prevGridId"); // null боломжтой
            Number prevFloor  = (Number) payload.get("prevFloorId");
            Integer prevFloorId = prevFloor != null ? prevFloor.intValue() : null;

            if (gridId == null) {
                // Хэрэглэгч disconnect → зөвхөн leave
                service.processLeave(prevGridId, floorId);
            } else {
                service.processEvent(gridId, floorId, prevGridId, prevFloorId);
            }

        } catch (Exception e) {
            log.error("Failed to process heatmap event: {}", e.getMessage());
        }
    }
}