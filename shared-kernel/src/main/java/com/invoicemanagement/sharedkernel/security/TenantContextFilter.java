package com.invoicemanagement.sharedkernel.security;

import com.invoicemanagement.sharedkernel.domain.TenantId;
import com.invoicemanagement.sharedkernel.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter to extract tenant context from HTTP requests.
 * Supports tenant identification via:
 * 1. X-Tenant-ID header (highest priority)
 * 2. JWT token claim (if authenticated)
 * 3. API key (future implementation)
 *
 * DDD Pattern: Infrastructure / Security
 * SOC2/GDPR: Ensures tenant isolation at the request boundary
 */
@Slf4j
@Component
public class TenantContextFilter extends OncePerRequestFilter {

    private static final String TENANT_HEADER = "X-Tenant-ID";
    private static final String TENANT_CLAIM = "tenantId";

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            String tenantId = extractTenantId(request);

            if (tenantId == null || tenantId.isBlank()) {
                log.warn("No tenant ID found in request: {} {}", request.getMethod(), request.getRequestURI());
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Tenant ID is required");
                return;
            }

            // Validate tenant exists and is active (future enhancement: check tenant registry)
            validateTenant(tenantId);

            // Set tenant context for this request
            TenantContext.setCurrentTenant(tenantId);
            log.debug("Tenant context set for request: {}", tenantId);

            // Continue filter chain
            filterChain.doFilter(request, response);

        } catch (IllegalArgumentException e) {
            log.error("Invalid tenant ID format: {}", e.getMessage());
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid tenant ID format");
        } catch (SecurityException e) {
            log.error("Tenant validation failed: {}", e.getMessage());
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied for tenant");
        } finally {
            // CRITICAL: Clear tenant context to prevent leakage across requests
            TenantContext.clear();
        }
    }

    /**
     * Extract tenant ID from request.
     * Priority: Header > JWT claim > API key
     */
    private String extractTenantId(HttpServletRequest request) {
        // 1. Check X-Tenant-ID header
        String tenantId = request.getHeader(TENANT_HEADER);
        if (tenantId != null && !tenantId.isBlank()) {
            log.debug("Tenant ID extracted from header: {}", tenantId);
            return tenantId;
        }

        // 2. Check JWT token claim
        tenantId = extractTenantFromAuthentication();
        if (tenantId != null && !tenantId.isBlank()) {
            log.debug("Tenant ID extracted from JWT: {}", tenantId);
            return tenantId;
        }

        // 3. Future: Extract from API key

        return null;
    }

    /**
     * Extract tenant ID from Spring Security authentication (JWT token).
     */
    private String extractTenantFromAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            Object principal = authentication.getPrincipal();

            // If using JWT with custom claims, extract tenantId claim
            // This is a placeholder - actual implementation depends on JWT library
            if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
                // Future: Extract from custom UserDetails implementation
                return null;
            }
        }
        return null;
    }

    /**
     * Validate tenant exists and is active.
     * Future enhancement: Query tenant registry database.
     */
    private void validateTenant(String tenantId) {
        try {
            UUID.fromString(tenantId);
            // TODO: Add actual tenant validation
            // - Check tenant exists in tenant_registry table
            // - Verify tenant status is ACTIVE
            // - Check tenant subscription is valid
            // - Enforce tenant-specific rate limits
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Tenant ID must be a valid UUID", e);
        }
    }

    /**
     * Skip filter for public endpoints (health checks, actuator, etc.).
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator") ||
               path.startsWith("/health") ||
               path.startsWith("/public");
    }
}
