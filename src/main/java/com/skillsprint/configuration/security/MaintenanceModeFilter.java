package com.skillsprint.configuration.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsprint.common.ApiResponse;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.service.system.MaintenanceStateHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gates non-public traffic while maintenance mode is active.
 *
 * <p>This filter only runs in the {@code @Order(2)} security chain — every endpoint listed in
 * {@code PUBLIC_ENDPOINTS} (health check, {@code /api/system/status}, the SePay webhook, {@code /ws/**}
 * and the unauthenticated auth endpoints) is served by the {@code @Order(1)} chain and never reaches
 * here, so we do not repeat them in the allowlist (single source of truth).</p>
 *
 * <p>The state is read from {@link MaintenanceStateHolder} (in-memory, TTL-cached) so there is no
 * per-request DB query. The admin bypass is <b>role-based</b>, not path-based: any caller carrying
 * {@code ROLE_ADMIN} keeps full access to the whole API, so admins can diagnose issues and toggle
 * maintenance off without locking themselves out.</p>
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MaintenanceModeFilter extends OncePerRequestFilter {

    static final String ROLE_ADMIN = "ROLE_ADMIN";

    MaintenanceStateHolder maintenanceStateHolder;
    ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (isAllowedDuringMaintenance(request) || !maintenanceStateHolder.isActive()) {
            filterChain.doFilter(request, response);
            return;
        }

        writeMaintenanceResponse(request, response);
    }

    private boolean isAllowedDuringMaintenance(HttpServletRequest request) {
        // CORS preflight must never be gated.
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        // Admins keep full access to every endpoint (not just /api/admin/**).
        if (hasAdminRole()) {
            return true;
        }
        // Authenticated auth operations (e.g. logout, oauth session) reach THIS chain and stay open
        // so users can sign out during maintenance. login/refresh are public-chain only.
        return request.getRequestURI().startsWith("/api/auth/");
    }

    private boolean hasAdminRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                        .anyMatch(authority -> ROLE_ADMIN.equals(authority.getAuthority()));
    }

    private void writeMaintenanceResponse(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ApiResponse<Object> body = ApiResponse.error(
                ErrorCode.MAINTENANCE_MODE,
                maintenanceStateHolder.getMessage(),
                request.getRequestURI()
        );

        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // Advertise UTF-8 so Vietnamese characters in the message decode correctly on every client.
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        // Standard 503 hint: tell clients/proxies when to retry, when an end time is scheduled.
        Instant endAt = maintenanceStateHolder.getEndAt();
        if (endAt != null) {
            long retryAfterSeconds = Duration.between(Instant.now(), endAt).getSeconds();
            if (retryAfterSeconds > 0) {
                response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
            }
        }

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
