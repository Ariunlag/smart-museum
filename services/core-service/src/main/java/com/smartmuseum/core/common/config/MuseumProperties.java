package com.smartmuseum.core.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "museum")
public class MuseumProperties {

    private Websocket websocket   = new Websocket();
    private Services  services    = new Services();
    private Mqtt      mqtt        = new Mqtt();

    public Websocket getWebsocket() { return websocket; }
    public Services  getServices()  { return services; }
    public Mqtt      getMqtt()      { return mqtt; }

    // ── WebSocket ────────────────────────────────────────
    public static class Websocket {
        private String path = "/ws";
        public String getPath()        { return path; }
        public void   setPath(String v){ this.path = v; }
    }

    // ── Services ─────────────────────────────────────────
    public static class Services {
        private ServiceConfig positioning = new ServiceConfig();
        private ServiceConfig artinfo     = new ServiceConfig();
        private ServiceConfig heatmap     = new ServiceConfig();

        public ServiceConfig getPositioning() { return positioning; }
        public ServiceConfig getArtinfo()     { return artinfo; }
        public ServiceConfig getHeatmap()     { return heatmap; }
    }

    // HTTP service config (url + enabled)
    public static class ServiceConfig {
        private boolean enabled    = true;
        private String  baseUrl;
        private String  endpoint;
        private int     timeoutMs  = 2000;

        public boolean isEnabled()          { return enabled; }
        public void    setEnabled(boolean v){ this.enabled = v; }
        public String  getBaseUrl()         { return baseUrl; }
        public void    setBaseUrl(String v) { this.baseUrl = v; }
        public String  getEndpoint()        { return endpoint; }
        public void    setEndpoint(String v){ this.endpoint = v; }
        public int     getTimeoutMs()       { return timeoutMs; }
        public void    setTimeoutMs(int v)  { this.timeoutMs = v; }
    }
    
    // ── MQTT ─────────────────────────────────────────────
    public static class Mqtt {
        private String brokerUrl    = "tcp://localhost:1883";
        private String clientId     = "core-service";
        private String heatmapTopic = "museum/heatmap/grid";

        public String getBrokerUrl()           { return brokerUrl; }
        public void   setBrokerUrl(String v)   { this.brokerUrl = v; }
        public String getClientId()            { return clientId; }
        public void   setClientId(String v)    { this.clientId = v; }
        public String getHeatmapTopic()        { return heatmapTopic; }
        public void   setHeatmapTopic(String v){ this.heatmapTopic = v; }
    }
}