package com.smartmuseum.artinfo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "museum")
public class MuseumProperties {

    private Admin    admin    = new Admin();
    private Building building = new Building();
    private Art      art      = new Art();

    public Admin    getAdmin()    { return admin; }
    public Building getBuilding() { return building; }
    public Art      getArt()      { return art; }

    // ── Admin ────────────────────────────────────────────
    public static class Admin {
        private String apiKey = "museum-admin-dev-key";
        public String getApiKey()         { return apiKey; }
        public void   setApiKey(String v) { this.apiKey = v; }
    }

    public static class Building {
        private int floors = 3;
        private int gridRows = 10;
        private int gridCols = 10;

        public int getFloors() { return floors; }
        public void setFloors(int v) { this.floors = v; }
        public int getGridRows() { return gridRows; }
        public void setGridRows(int v) { this.gridRows = v; }
        public int getGridCols() { return gridCols; }
        public void setGridCols(int v) { this.gridCols = v; }
    }

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