package com.smartmuseum.heatmap.store;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;

@Document(collection = "heatmap")
@CompoundIndex(def = "{'gridId': 1, 'floorId': 1}", unique = true)
public class GridCount {

    @Id
    private String id;
    private String gridId;
    private int    floorId;
    private int    count;       // Одоо байгаа хүний тоо
    private long   updatedAt;

    public GridCount() {}

    public GridCount(String gridId, int floorId) {
        this.gridId    = gridId;
        this.floorId   = floorId;
        this.count     = 0;
        this.updatedAt = System.currentTimeMillis();
    }

    public String getId()        { return id; }
    public String getGridId()    { return gridId; }
    public int    getFloorId()   { return floorId; }
    public int    getCount()     { return count; }
    public long   getUpdatedAt() { return updatedAt; }

    public void setCount(int v)     { this.count = v; }
    public void setUpdatedAt(long v){ this.updatedAt = v; }
}