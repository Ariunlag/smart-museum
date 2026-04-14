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
        private int floorHeightMeters = 4;

        public int getFloors()             { return floors; }
        public void setFloors(int v)       { this.floors = v; }
        public int getGridRows()           { return gridRows; }
        public void setGridRows(int v)     { this.gridRows = v; }
        public int getGridCols()           { return gridCols; }
        public void setGridCols(int v)     { this.gridCols = v; }
        public int getCellSizeMeters()     { return cellSizeMeters; }
        public void setCellSizeMeters(int v){ this.cellSizeMeters = v; }
        public int getFloorHeightMeters()     { return floorHeightMeters; }
        public void setFloorHeightMeters(int v){ this.floorHeightMeters = v; }
    }

    // ── Beacon тохиргоо ──────────────────────────────────
    public static class Beacons {
        private int              total        = 20;
        private int              defaultRssi  = -100;
        private RadioModel       model        = new RadioModel();
        private List<FloorBeacon> floorBeacons = new ArrayList<>();
        private List<BeaconPosition> positions = new ArrayList<>();

        public int getTotal()                      { return total; }
        public void setTotal(int v)                { this.total = v; }
        public int getDefaultRssi()                { return defaultRssi; }
        public void setDefaultRssi(int v)          { this.defaultRssi = v; }
        public RadioModel getModel()               { return model; }
        public void setModel(RadioModel v)         { this.model = v; }
        public List<FloorBeacon> getFloorBeacons() { return floorBeacons; }
        public void setFloorBeacons(List<FloorBeacon> v){ this.floorBeacons = v; }
        public List<BeaconPosition> getPositions() { return positions; }
        public void setPositions(List<BeaconPosition> v){ this.positions = v; }
    }

    public static class RadioModel {
        private double txPowerDb = -59.0;
        private double pathLossExponent = 2.2;
        private double maxRangeMeters = 18.0;
        private double referenceDistanceMeters = 1.0;
        private int minRssi = -100;
        private int maxRssi = -30;
        private int cutoffRssi = -85;

        public double getTxPowerDb()                  { return txPowerDb; }
        public void setTxPowerDb(double v)            { this.txPowerDb = v; }
        public double getPathLossExponent()           { return pathLossExponent; }
        public void setPathLossExponent(double v)     { this.pathLossExponent = v; }
        public double getMaxRangeMeters()             { return maxRangeMeters; }
        public void setMaxRangeMeters(double v)       { this.maxRangeMeters = v; }
        public double getReferenceDistanceMeters()    { return referenceDistanceMeters; }
        public void setReferenceDistanceMeters(double v){ this.referenceDistanceMeters = v; }
        public int getMinRssi()                       { return minRssi; }
        public void setMinRssi(int v)                 { this.minRssi = v; }
        public int getMaxRssi()                       { return maxRssi; }
        public void setMaxRssi(int v)                 { this.maxRssi = v; }
        public int getCutoffRssi()                    { return cutoffRssi; }
        public void setCutoffRssi(int v)              { this.cutoffRssi = v; }
    }

    public static class BeaconPosition {
        private String id;
        private int floor = 1;
        private int x;
        private int y;
        private String role = "normal";
        private Double txPowerDb;
        private Double pathLossExponent;
        private Double maxRangeMeters;
        private Integer cutoffRssi;

        public String getId()       { return id; }
        public void setId(String v) { this.id = v; }
        public int getFloor()       { return floor; }
        public void setFloor(int v) { this.floor = v; }
        public int getX()           { return x; }
        public void setX(int v)     { this.x = v; }
        public int getY()           { return y; }
        public void setY(int v)     { this.y = v; }
        public String getRole()       { return role; }
        public void setRole(String v) { this.role = v; }
        public Double getTxPowerDb()     { return txPowerDb; }
        public void setTxPowerDb(Double v){ this.txPowerDb = v; }
        public Double getPathLossExponent()     { return pathLossExponent; }
        public void setPathLossExponent(Double v){ this.pathLossExponent = v; }
        public Double getMaxRangeMeters()     { return maxRangeMeters; }
        public void setMaxRangeMeters(Double v){ this.maxRangeMeters = v; }
        public Integer getCutoffRssi()              { return cutoffRssi; }
        public void setCutoffRssi(Integer v)        { this.cutoffRssi = v; }
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