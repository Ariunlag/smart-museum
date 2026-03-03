package com.smartmuseum.artinfo.repository;

import com.smartmuseum.artinfo.domain.Art;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ArtRepository extends MongoRepository<Art, String> {

    // GridId-р хайна (яг таарсан)
    List<Art> findByGridIdAndFloorId(String gridId, int floorId);

    // Floor дотор бүх art
    List<Art> findByFloorId(int floorId);

    // Seed хийгдсэн эсэхийг шалгана
    long countByFloorId(int floorId);
}