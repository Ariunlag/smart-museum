package com.smartmuseum.artinfo.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

@Document(collection = "arts")
public class Art {

    @Id
    private String id;

    private String title;
    private String artist;
    private String description;

    @Indexed
    private String gridId;    // "B3"

    @Indexed
    private int    floorId;   // 1, 2, 3

    private int    x;
    private int    y;

    public Art() {}

    public Art(String title, String artist, String description,
               String gridId, int floorId, int x, int y) {
        this.title       = title;
        this.artist      = artist;
        this.description = description;
        this.gridId      = gridId;
        this.floorId     = floorId;
        this.x           = x;
        this.y           = y;
    }

    public String getId()          { return id; }
    public String getTitle()       { return title; }
    public String getArtist()      { return artist; }
    public String getDescription() { return description; }
    public String getGridId()      { return gridId; }
    public int    getFloorId()     { return floorId; }
    public int    getX()           { return x; }
    public int    getY()           { return y; }
}