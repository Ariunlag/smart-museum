package com.smartmuseum.positioning.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "qdrant")
public class QdrantProperties {
    private String baseUrl    = "http://localhost:6333";
    private String collection = "fingerprints";

    public String getBaseUrl()             { return baseUrl; }
    public void   setBaseUrl(String v)     { this.baseUrl = v; }
    public String getCollection()          { return collection; }
    public void   setCollection(String v)  { this.collection = v; }
}