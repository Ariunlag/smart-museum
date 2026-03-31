package com.smartmuseum.core.registry.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Museum service-н бүртгэлийн мэдээлэл.
 * MD файлын Registry Data Model-г хэрэгжүүлнэ.
 */
@Document(collection = "service_registry")
public class ServiceRecord {

    public enum Status { ACTIVE, OFFLINE, DISABLED, REMOVED }

    @Id
    private String id;

    @Indexed(unique = true)
    private String serviceId;       // "positioning-service"
    private String serviceName;     // "Positioning Service"
    private String serviceUrl;      // "http://localhost:8081"
    private String version;         // "1.0.0"
    private Status status;          // ACTIVE | OFFLINE | DISABLED | REMOVED

    private long   firstSeenAt;
    private long   lastHeartbeatAt;
    private long   lastUpdatedAt;

    private String disabledBy;      // Admin user id
    private String removedBy;       // Admin user id

    public ServiceRecord() {}

    public ServiceRecord(String serviceId, String serviceName,
                         String serviceUrl, String version) {
        this.serviceId       = serviceId;
        this.serviceName     = serviceName;
        this.serviceUrl      = serviceUrl;
        this.version         = version;
        this.status          = Status.ACTIVE;
        this.firstSeenAt     = System.currentTimeMillis();
        this.lastHeartbeatAt = System.currentTimeMillis();
        this.lastUpdatedAt   = System.currentTimeMillis();
    }

    // ── Getters & Setters ────────────────────────────────
    public String getId()              { return id; }
    public String getServiceId()       { return serviceId; }
    public void   setServiceId(String v){ this.serviceId = v; }
    public String getServiceName()     { return serviceName; }
    public void   setServiceName(String v){ this.serviceName = v; }
    public String getServiceUrl()      { return serviceUrl; }
    public void   setServiceUrl(String v){ this.serviceUrl = v; }
    public String getVersion()         { return version; }
    public void   setVersion(String v) { this.version = v; }
    public Status getStatus()          { return status; }
    public void   setStatus(Status v)  { this.status = v; }
    public long   getFirstSeenAt()     { return firstSeenAt; }
    public void   setFirstSeenAt(long v){ this.firstSeenAt = v; }
    public long   getLastHeartbeatAt() { return lastHeartbeatAt; }
    public void   setLastHeartbeatAt(long v){ this.lastHeartbeatAt = v; }
    public long   getLastUpdatedAt()   { return lastUpdatedAt; }
    public void   setLastUpdatedAt(long v){ this.lastUpdatedAt = v; }
    public String getDisabledBy()      { return disabledBy; }
    public void   setDisabledBy(String v){ this.disabledBy = v; }
    public String getRemovedBy()       { return removedBy; }
    public void   setRemovedBy(String v){ this.removedBy = v; }
}