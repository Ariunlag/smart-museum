package com.smartmuseum.heatmap.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Map;

/**
 * Service бүр энэ class-г ашиглан:
 *   1. Startup-д register publish хийнэ
 *   2. 30 секунд тутам heartbeat publish хийнэ
 *
 * application.yml тохиргоо:
 *   service.enabled=false бол publish хийхгүй
 */
@Component
@EnableScheduling
public class ServiceRegistrar {

    private static final Logger log = LoggerFactory.getLogger(ServiceRegistrar.class);

    @Value("${service.enabled:true}")
    private boolean enabled;

    @Value("${service.id}")
    private String serviceId;

    @Value("${service.name}")
    private String serviceName;

    @Value("${service.url}")
    private String serviceUrl;

    @Value("${service.version:1.0.0}")
    private String version;

    @Value("${registry.mqtt.broker-url}")
    private String brokerUrl;

    @Value("${registry.mqtt.register-topic}")
    private String registerTopic;

    @Value("${registry.mqtt.heartbeat-topic}")
    private String heartbeatTopic;

    private MqttClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        if (!enabled) {
            log.info("ServiceRegistrar disabled for {}", serviceId);
            return;
        }
        try {
            client = new MqttClient(brokerUrl, serviceId + "-registrar", null);
            var opts = new MqttConnectOptions();
            opts.setAutomaticReconnect(true);
            opts.setCleanSession(true);
            client.connect(opts);
            publishRegister();
        } catch (Exception e) {
            log.error("ServiceRegistrar failed to connect: {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${service.heartbeat-interval-ms:30000}")
    public void heartbeat() {
        if (!enabled || client == null || !client.isConnected()) return;
        try {
            var payload = Map.of(
                    "serviceId", serviceId,
                    "timestamp", System.currentTimeMillis()
            );
            publish(heartbeatTopic, payload);
        } catch (Exception e) {
            log.warn("Heartbeat failed: {}", e.getMessage());
        }
    }

    private void publishRegister() throws Exception {
        var payload = Map.of(
                "serviceId",   serviceId,
                "serviceName", serviceName,
                "serviceUrl",  serviceUrl,
                "version",     version,
                "timestamp",   System.currentTimeMillis()
        );
        publish(registerTopic + "/" + serviceId, payload);
        log.info("Registered service: {} → {}", serviceId, registerTopic);
    }

    private void publish(String topic, Object payload) throws Exception {
        String json = mapper.writeValueAsString(payload);
        MqttMessage msg = new MqttMessage(json.getBytes());
        msg.setRetained(true);  
        msg.setQos(1);          
        client.publish(topic, msg);
    }
}