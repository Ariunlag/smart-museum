package com.smartmuseum.heatmap.store;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GridCountRepository extends MongoRepository<GridCount, String> {
    Optional<GridCount> findByGridIdAndFloorId(String gridId, int floorId);
    List<GridCount> findByFloorId(int floorId);
}