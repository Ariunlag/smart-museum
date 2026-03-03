package com.smartmuseum.positioning.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "museum")
public class MuseumProperties {

    private Building building = new Building();
    private Beacons  beacons  = new Beacons();
    private Floor    floor    = new Floor();
    private Art      art      = new Art();

    public Building getBuilding() { return building; }
    public Beacons  getBeacons()  { return beacons; }
    public Floor    getFloor()    { return floor; }
    public Art      getArt()      { return art; }

    // ── Байшингийн тохиргоо ──────────────────────────────
    public static class Building {
        private int floors         = 1;
        private int gridRows       = 10;
        private int gridCols       = 10;
        private int cellSizeMeters = 2;

        public int getFloors()             { return floors; }
        public void setFloors(int v)       { this.floors = v; }
        public int getGridRows()           { return gridRows; }
        public void setGridRows(int v)     { this.gridRows = v; }
        public int getGridCols()           { return gridCols; }
        public void setGridCols(int v)     { this.gridCols = v; }
        public int getCellSizeMeters()     { return cellSizeMeters; }
        public void setCellSizeMeters(int v){ this.cellSizeMeters = v; }
    }

    // ── Beacon тохиргоо ──────────────────────────────────
    public static class Beacons {
        private int              total        = 20;
        private int              defaultRssi  = -100;
        private List<FloorBeacon> floorBeacons = new ArrayList<>();

        public int getTotal()                      { return total; }
        public void setTotal(int v)                { this.total = v; }
        public int getDefaultRssi()                { return defaultRssi; }
        public void setDefaultRssi(int v)          { this.defaultRssi = v; }
        public List<FloorBeacon> getFloorBeacons() { return floorBeacons; }
        public void setFloorBeacons(List<FloorBeacon> v){ this.floorBeacons = v; }
    }

    public static class FloorBeacon {
        private String id;
        private int    floor;

        public String getId()       { return id; }
        public void setId(String v) { this.id = v; }
        public int getFloor()       { return floor; }
        public void setFloor(int v) { this.floor = v; }
    }

    // ── Floor detection strategy ─────────────────────────
    public static class Floor {
        private String strategy = "beacon"; // beacon | manual | qr
        public String getStrategy()        { return strategy; }
        public void setStrategy(String v)  { this.strategy = v; }
    }

    // ── Art хайлтын тохиргоо ─────────────────────────────
    public static class Art {
        private int nearbyRange = 2;
        private int maxResults  = 2;

        public int getNearbyRange()        { return nearbyRange; }
        public void setNearbyRange(int v)  { this.nearbyRange = v; }
        public int getMaxResults()         { return maxResults; }
        public void setMaxResults(int v)   { this.maxResults = v; }
    }
}