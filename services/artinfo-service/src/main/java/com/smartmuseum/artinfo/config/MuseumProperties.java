package com.smartmuseum.artinfo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "museum")
public class MuseumProperties {

    private Art art = new Art();

    public Art getArt() { return art; }

    public static class Art {
        private int nearbyRange = 2;
        private int maxResults  = 2;
        private int seedCount   = 100;

        public int getNearbyRange()       { return nearbyRange; }
        public void setNearbyRange(int v) { this.nearbyRange = v; }
        public int getMaxResults()        { return maxResults; }
        public void setMaxResults(int v)  { this.maxResults = v; }
        public int getSeedCount()         { return seedCount; }
        public void setSeedCount(int v)   { this.seedCount = v; }
    }
}