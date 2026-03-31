package com.smartmuseum.core.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "museum")
public class MuseumProperties {

    private String    spaceId   = "smart-museum";
    private Websocket websocket = new Websocket();
    private Services  services  = new Services();
    private Mqtt      mqtt      = new Mqtt();
    private Registry  registry  = new Registry();

    public String    getSpaceId()   { return spaceId; }
    public void      setSpaceId(String v){ this.spaceId = v; }
    public Websocket getWebsocket() { return websocket; }
    public Services  getServices()  { return services; }
    public Mqtt      getMqtt()      { return mqtt; }
    public Registry  getRegistry()  { return registry; }

    // ── WebSocket ────────────────────────────────────────
    public static class Websocket {
        private String path = "/ws";
        private long inactivityTimeoutMs = 180000;
        private long cleanupIntervalMs = 30000;
        public String getPath()        { return path; }
        public void   setPath(String v){ this.path = v; }
        public long getInactivityTimeoutMs() { return inactivityTimeoutMs; }
        public void setInactivityTimeoutMs(long v) { this.inactivityTimeoutMs = v; }
        public long getCleanupIntervalMs() { return cleanupIntervalMs; }
        public void setCleanupIntervalMs(long v) { this.cleanupIntervalMs = v; }
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

    public static class ServiceConfig {
        private boolean enabled   = true;
        private String  baseUrl;
        private String  endpoint;
        private int     timeoutMs = 2000;

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
        private String brokerUrl = "tcp://localhost:1883";
        private String clientId  = "core-service";
        private Topics topics    = new Topics();

        public String getBrokerUrl()           { return brokerUrl; }
        public void   setBrokerUrl(String v)   { this.brokerUrl = v; }
        public String getClientId()            { return clientId; }
        public void   setClientId(String v)    { this.clientId = v; }
        public Topics getTopics()              { return topics; }

        public static class Topics {
            private String heatmap           = "museum/heatmap/grid";
            private String registryRegister  = "museum/registry/register";
            private String registryHeartbeat = "museum/registry/heartbeat";

            public String getHeatmap()                  { return heatmap; }
            public void   setHeatmap(String v)          { this.heatmap = v; }
            public String getRegistryRegister()         { return registryRegister; }
            public void   setRegistryRegister(String v) { this.registryRegister = v; }
            public String getRegistryHeartbeat()        { return registryHeartbeat; }
            public void   setRegistryHeartbeat(String v){ this.registryHeartbeat = v; }
        }
    }

    // ── Registry ─────────────────────────────────────────
    public static class Registry {
        private long heartbeatTimeoutMs = 60000;
        private long checkIntervalMs    = 15000;

        public long getHeartbeatTimeoutMs()        { return heartbeatTimeoutMs; }
        public void setHeartbeatTimeoutMs(long v)  { this.heartbeatTimeoutMs = v; }
        public long getCheckIntervalMs()           { return checkIntervalMs; }
        public void setCheckIntervalMs(long v)     { this.checkIntervalMs = v; }
    }
}