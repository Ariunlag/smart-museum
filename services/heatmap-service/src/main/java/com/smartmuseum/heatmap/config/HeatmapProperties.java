package com.smartmuseum.heatmap.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "museum")
public class HeatmapProperties {

    private Mqtt    mqtt    = new Mqtt();
    private Heatmap heatmap = new Heatmap();

    public Mqtt    getMqtt()    { return mqtt; }
    public Heatmap getHeatmap() { return heatmap; }

    public static class Mqtt {
        private String brokerUrl    = "tcp://localhost:1883";
        private String clientId     = "heatmap-service";
        private String heatmapTopic = "museum/heatmap/grid";

        public String getBrokerUrl()           { return brokerUrl; }
        public void   setBrokerUrl(String v)   { this.brokerUrl = v; }
        public String getClientId()            { return clientId; }
        public void   setClientId(String v)    { this.clientId = v; }
        public String getHeatmapTopic()        { return heatmapTopic; }
        public void   setHeatmapTopic(String v){ this.heatmapTopic = v; }
    }

    public static class Heatmap {
        private int persistIntervalMinutes = 5;
        private int crowdThreshold         = 10;

        public int getPersistIntervalMinutes()        { return persistIntervalMinutes; }
        public void setPersistIntervalMinutes(int v)  { this.persistIntervalMinutes = v; }
        public int getCrowdThreshold()                { return crowdThreshold; }
        public void setCrowdThreshold(int v)          { this.crowdThreshold = v; }
    }
}