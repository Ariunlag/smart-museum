package com.smartmuseum.core.integration.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmuseum.core.common.config.MuseumProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class HeatmapPublisher {

    private static final Logger log = LoggerFactory.getLogger(HeatmapPublisher.class);

    private final MqttPahoMessageHandler mqttOutbound;
    private final MuseumProperties       props;
    private final ObjectMapper           mapper;

    public HeatmapPublisher(MqttPahoMessageHandler mqttOutbound,
                            MuseumProperties props,
                            ObjectMapper mapper) {
        this.mqttOutbound = mqttOutbound;
        this.props        = props;
        this.mapper       = mapper;
    }

    /**
     * Publishes one event when location changes.
     * prevGridId -> decrements heatmap count (null on first entry)
     * gridId     -> increments heatmap count
     */
    public void publish(String deviceId,
                        long sequenceNum,
                        String gridId,
                        int floorId,
                        String prevGridId,
                        Integer prevFloorId) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("eventId",   UUID.randomUUID().toString());
            payload.put("version",   1);
            payload.put("deviceId",  deviceId);
            payload.put("sequenceNum", sequenceNum);
            payload.put("gridId",    gridId);
            payload.put("floorId",   floorId);
            payload.put("prevGridId", prevGridId); // null бол анхны орох
            payload.put("prevFloorId", prevFloorId);
            payload.put("timestamp", System.currentTimeMillis());

            var json = mapper.writeValueAsString(payload);
            var msg  = MessageBuilder
                    .withPayload(json)
                    .setHeader("mqtt_topic", props.getMqtt().getTopics().getHeatmap())
                    .build();
            mqttOutbound.handleMessage(msg);
            log.info("Heatmap published: eventId={} deviceId={} seq={} {}:{} -> {}:{}",
                    payload.get("eventId"), deviceId, sequenceNum, prevFloorId, prevGridId, floorId, gridId);
        } catch (Exception e) {
            log.warn("Heatmap MQTT publish failed: {}", e.getMessage());
        }
    }

    public void publishLeave(String deviceId, long sequenceNum, String prevGridId, int prevFloorId) {
        publish(deviceId, sequenceNum, null, prevFloorId, prevGridId, prevFloorId);
    }
}