package com.smartmuseum.core.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartmuseum.core.common.config.MuseumProperties;
import org.eclipse.paho.client.mqttv3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Map;

@Component
public class RegistryMqttSubscriber {

    private static final Logger log = LoggerFactory.getLogger(RegistryMqttSubscriber.class);

    private final MuseumProperties props;
    private final RegistryService  registryService;
    private final ObjectMapper     mapper;

    public RegistryMqttSubscriber(MuseumProperties props,
                                  RegistryService registryService,
                                  ObjectMapper mapper) {
        this.props           = props;
        this.registryService = registryService;
        this.mapper          = mapper;
    }

    @PostConstruct
    public void init() {
        try {
            var mqtt   = props.getMqtt();
            var client = new MqttClient(
                    mqtt.getBrokerUrl(),
                    mqtt.getClientId() + "-registry-sub",
                    null
            );
            var opts = new MqttConnectOptions();
            opts.setAutomaticReconnect(true);
            opts.setCleanSession(true);
            client.connect(opts);

            // Topic нэрийг spaceId + hardcoded suffix-ээр үүсгэнэ
            String spaceId        = props.getSpaceId();
            String registerTopic  = spaceId + "/registry/register";
            String heartbeatTopic = spaceId + "/registry/heartbeat";

            // wildcard 
            client.subscribe(registerTopic + "/+",  (topic, msg) -> handleRegister(msg));
            client.subscribe(heartbeatTopic + "/+", (topic, msg) -> handleHeartbeat(msg));

            log.info("Registry subscriber listening: {}, {}", registerTopic, heartbeatTopic);

        } catch (MqttException e) {
            log.error("Registry MQTT subscriber failed: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void handleRegister(MqttMessage msg) {
        try {
            var payload = mapper.readValue(new String(msg.getPayload()), Map.class);
            registryService.onRegister(payload);
        } catch (Exception e) {
            log.error("Registry: invalid register payload: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void handleHeartbeat(MqttMessage msg) {
        try {
            var payload = mapper.readValue(new String(msg.getPayload()), Map.class);
            registryService.onHeartbeat(payload);
        } catch (Exception e) {
            log.error("Registry: invalid heartbeat payload: {}", e.getMessage());
        }
    }
}