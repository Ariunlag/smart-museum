package com.smartmuseum.core.registry.domain;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceRecordRepository extends MongoRepository<ServiceRecord, String> {
    Optional<ServiceRecord> findByServiceId(String serviceId);
    List<ServiceRecord> findByStatus(ServiceRecord.Status status);
    List<ServiceRecord> findByStatusNot(ServiceRecord.Status status);
}