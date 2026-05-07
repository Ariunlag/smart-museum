package com.smartmuseum.artinfo.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class ServiceAvailabilityFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ServiceAvailabilityFilter.class);

    private final ObjectMapper objectMapper;
    private final HttpClient   httpClient;
    private final String       coreBaseUrl;
    private final String       serviceId;
    private final long         checkIntervalMs;

    private volatile boolean active = true;
    private volatile long    lastCheckAt = 0L;

    public ServiceAvailabilityFilter(
            ObjectMapper objectMapper,
            @Value("${museum.core.base-url:http://localhost:8080}") String coreBaseUrl,
            @Value("${service.id:artinfo-service}") String serviceId,
            @Value("${museum.core.status-check-interval-ms:3000}") long checkIntervalMs) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
        this.coreBaseUrl = coreBaseUrl;
        this.serviceId = serviceId;
        this.checkIntervalMs = checkIntervalMs;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        refreshStatusIfNeeded();

        if (!active) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"SERVICE_DISABLED\",\"serviceId\":\"" + serviceId + "\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void refreshStatusIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastCheckAt < checkIntervalMs) {
            return;
        }

        synchronized (this) {
            now = System.currentTimeMillis();
            if (now - lastCheckAt < checkIntervalMs) {
                return;
            }
            lastCheckAt = now;

            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(coreBaseUrl + "/services"))
                        .timeout(Duration.ofSeconds(2))
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    boolean shouldBeActive = isServiceListedAsActive(response.body());
                    if (active != shouldBeActive) {
                        log.info("Service availability changed: {} -> {}", serviceId, shouldBeActive ? "ACTIVE" : "DISABLED");
                    }
                    active = shouldBeActive;
                }
            } catch (Exception ex) {
                log.warn("Could not check service status from core ({}): {}", coreBaseUrl, ex.getMessage());
            }
        }
    }

    private boolean isServiceListedAsActive(String body) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        if (!root.isArray()) {
            return true;
        }
        for (JsonNode node : root) {
            if (serviceId.equals(node.path("serviceId").asText())) {
                String status = node.path("status").asText();
                if (status == null || status.isEmpty()) {
                    return true;
                }
                return "ACTIVE".equalsIgnoreCase(status);
            }
        }
        return false;
    }
}
