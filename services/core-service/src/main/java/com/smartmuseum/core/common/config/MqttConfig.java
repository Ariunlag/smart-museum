package com.smartmuseum.core.common.config;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;

@Configuration
public class MqttConfig {

    private final MuseumProperties props;

    public MqttConfig(MuseumProperties props) {
        this.props = props;
    }

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        var factory = new DefaultMqttPahoClientFactory();
        var opts = new MqttConnectOptions();
        opts.setServerURIs(new String[]{ props.getMqtt().getBrokerUrl() });
        opts.setCleanSession(true);
        opts.setAutomaticReconnect(true);
        factory.setConnectionOptions(opts);
        return factory;
    }

    @Bean
    public MqttPahoMessageHandler mqttOutbound(MqttPahoClientFactory factory) {
        var handler = new MqttPahoMessageHandler(
                props.getMqtt().getClientId() + "-pub",
                factory
        );
        handler.setAsync(true);
        handler.setDefaultTopic(props.getMqtt().getHeatmapTopic());
        return handler;
    }
}