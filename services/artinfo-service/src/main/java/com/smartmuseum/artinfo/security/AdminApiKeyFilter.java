package com.smartmuseum.artinfo.security;

import com.smartmuseum.artinfo.config.MuseumProperties;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Servlet filter that requires an X-Admin-Key header on all /internal/art/admin/** requests.
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

        if (request.getRequestURI().startsWith("/internal/art/admin/")) {
            String key = request.getHeader("X-Admin-Key");
            if (!adminKey.equals(key)) {
                log.warn("Unauthorized /internal/art/admin request from {}: path={}",
                        request.getRemoteAddr(), request.getRequestURI());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Unauthorized: missing or invalid X-Admin-Key header\"}");
                return;
            }
        }
        chain.doFilter(req, res);
    }
}
