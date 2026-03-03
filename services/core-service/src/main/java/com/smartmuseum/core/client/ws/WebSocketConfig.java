package com.smartmuseum.core.client.ws;

import com.smartmuseum.core.common.config.MuseumProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;

/**
 * WebSocket endpoint тохируулна.
 * Path: application.yml-с авна (museum.websocket.path)
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final DeviceWebSocketHandler handler;
    private final MuseumProperties       props;

    public WebSocketConfig(DeviceWebSocketHandler handler, MuseumProperties props) {
        this.handler = handler;
        this.props   = props;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, props.getWebsocket().getPath())
                .setAllowedOrigins("*");
    }
}