package com.smartmuseum.heatmap.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmuseum.heatmap.service.HeatmapEventGuard;
import com.smartmuseum.heatmap.service.HeatmapService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Processes MQTT events received from core-service.
 *
 * Expected payload:
 * {
 *   "eventId":    "uuid",
 *   "deviceId":   "phone-123",
 *   "sequenceNum": 7,
 *   "gridId":     "C3",       // null when device is leaving
 *   "floorId":    1,
 *   "prevGridId": "B2",       // null on first entry
 *   "prevFloorId": 1,
 *   "timestamp":  123456
 * }
 */
@Component
public class HeatmapSubscriber {

    private static final Logger log = LoggerFactory.getLogger(HeatmapSubscriber.class);

    private final HeatmapEventGuard eventGuard;
    private final HeatmapService    service;
    private final ObjectMapper      mapper;

    public HeatmapSubscriber(HeatmapEventGuard eventGuard,
                             HeatmapService service,
                             ObjectMapper mapper) {
        this.eventGuard = eventGuard;
        this.service    = service;
        this.mapper     = mapper;
    }

    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void handle(Message<String> message) {
        try {
            var payload = mapper.readValue(message.getPayload(), Map.class);

            String eventId  = (String) payload.get("eventId");
            String deviceId = (String) payload.get("deviceId");
            Number seqNum   = (Number) payload.get("sequenceNum");
            Long sequenceNum = seqNum != null ? seqNum.longValue() : null;

            if (!eventGuard.shouldProcess(eventId, deviceId, sequenceNum)) {
                log.debug("Skipping duplicate or out-of-order heatmap event: eventId={} deviceId={} seq={}",
                        eventId, deviceId, sequenceNum);
                return;
            }

            String  gridId     = (String) payload.get("gridId");
            Number  floorNum   = (Number) payload.get("floorId");
            int     floorId    = floorNum != null ? floorNum.intValue() : -1;
            String  prevGridId = (String) payload.get("prevGridId");
            Number  prevFloor  = (Number) payload.get("prevFloorId");
            Integer prevFloorId = prevFloor != null ? prevFloor.intValue() : null;

            if (gridId == null) {
                // Device disconnect — decrement previous cell counter
                if (prevFloorId == null) {
                    log.warn("Skipping leave event without prevFloorId: eventId={} deviceId={}", eventId, deviceId);
                    return;
                }
                if (prevGridId == null) {
                    log.warn("Skipping leave event without prevGridId: eventId={} deviceId={}", eventId, deviceId);
                    return;
                }
                service.processLeave(prevGridId, prevFloorId);
            } else {
                if (floorId < 0) {
                    log.warn("Skipping event without valid floorId: eventId={} deviceId={} gridId={}",
                            eventId, deviceId, gridId);
                    return;
                }
                service.processEvent(gridId, floorId, prevGridId, prevFloorId);
            }

        } catch (Exception e) {
            log.error("Failed to process heatmap event: {}", e.getMessage());
        }
    }
}
