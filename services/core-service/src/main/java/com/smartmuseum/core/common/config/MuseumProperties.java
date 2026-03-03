package com.smartmuseum.core.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "museum")
public class MuseumProperties {

    private Websocket   websocket   = new Websocket();
    private Positioning positioning = new Positioning();
    private ArtInfo     artInfo     = new ArtInfo();
    private Mqtt        mqtt        = new Mqtt();

    public Websocket   getWebsocket()   { return websocket; }
    public Positioning getPositioning() { return positioning; }
    public ArtInfo     getArtInfo()     { return artInfo; }
    public Mqtt        getMqtt()        { return mqtt; }

    // ── WebSocket ────────────────────────────────────────
    public static class Websocket {
        private String path = "/ws";
        public String getPath()        { return path; }
        public void   setPath(String v){ this.path = v; }
    }

    // ── Positioning service ──────────────────────────────
    public static class Positioning {
        private String baseUrl    = "http://localhost:8081";
        private String endpoint   = "/internal/positioning/readings";
        private int    timeoutMs  = 2000;

        public String getBaseUrl()         { return baseUrl; }
        public void   setBaseUrl(String v) { this.baseUrl = v; }
        public String getEndpoint()        { return endpoint; }
        public void   setEndpoint(String v){ this.endpoint = v; }
        public int    getTimeoutMs()       { return timeoutMs; }
        public void   setTimeoutMs(int v)  { this.timeoutMs = v; }
    }

    // ── ArtInfo service ──────────────────────────────────
    public static class ArtInfo {
        private String baseUrl   = "http://localhost:8082";
        private String endpoint  = "/internal/art/nearest";
        private int    timeoutMs = 2000;

        public String getBaseUrl()         { return baseUrl; }
        public void   setBaseUrl(String v) { this.baseUrl = v; }
        public String getEndpoint()        { return endpoint; }
        public void   setEndpoint(String v){ this.endpoint = v; }
        public int    getTimeoutMs()       { return timeoutMs; }
        public void   setTimeoutMs(int v)  { this.timeoutMs = v; }
    }

    // ── MQTT ─────────────────────────────────────────────
    public static class Mqtt {
        private String brokerUrl     = "tcp://localhost:1883";
        private String clientId      = "core-service";
        private String heatmapTopic  = "museum/heatmap/grid";

        public String getBrokerUrl()          { return brokerUrl; }
        public void   setBrokerUrl(String v)  { this.brokerUrl = v; }
        public String getClientId()           { return clientId; }
        public void   setClientId(String v)   { this.clientId = v; }
        public String getHeatmapTopic()       { return heatmapTopic; }
        public void   setHeatmapTopic(String v){ this.heatmapTopic = v; }
    }
}