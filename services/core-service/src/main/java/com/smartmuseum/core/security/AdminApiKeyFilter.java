package com.smartmuseum.core.security;

import com.smartmuseum.core.common.config.MuseumProperties;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Servlet filter that requires an X-Admin-Key header on all /admin/** requests.
 * Configure the key via museum.admin.api-key in application.yml (or override via MUSEUM_ADMIN_API_KEY env var).
 */
@Component
@Order(1)
public class AdminApiKeyFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(AdminApiKeyFilter.class);

    private final String adminKey;

    public AdminApiKeyFilter(MuseumProperties props) {
        this.adminKey = props.getAdmin().getApiKey();
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest  request  = (HttpServletRequest)  req;
        HttpServletResponse response = (HttpServletResponse) res;

        addCorsHeaders(request, response);

        // Answer CORS preflight requests before admin auth checks.
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }

        if (request.getRequestURI().startsWith("/admin/")) {
            String key = request.getHeader("X-Admin-Key");
            if (!adminKey.equals(key)) {
                log.warn("Unauthorized /admin request from {}: path={}",
                        request.getRemoteAddr(), request.getRequestURI());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Unauthorized: missing or invalid X-Admin-Key header\"}");
                return;
            }
        }
        chain.doFilter(req, res);
    }

    private void addCorsHeaders(HttpServletRequest request, HttpServletResponse response) {
        String origin = request.getHeader("Origin");
        response.setHeader("Access-Control-Allow-Origin",
                origin == null || origin.isBlank() ? "*" : origin);
        response.setHeader("Vary", "Origin");
        response.setHeader("Access-Control-Allow-Methods", "GET,POST,PUT,PATCH,DELETE,OPTIONS");

        String requestedHeaders = request.getHeader("Access-Control-Request-Headers");
        response.setHeader("Access-Control-Allow-Headers",
                requestedHeaders == null || requestedHeaders.isBlank()
                        ? "Content-Type,X-Admin-Key"
                        : requestedHeaders);
        response.setHeader("Access-Control-Max-Age", "3600");
    }
}
