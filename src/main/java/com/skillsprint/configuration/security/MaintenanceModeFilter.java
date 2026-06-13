package com.skillsprint.configuration.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsprint.common.ApiResponse;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.service.system.SystemMaintenanceService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MaintenanceModeFilter extends OncePerRequestFilter {

    SystemMaintenanceService maintenanceService;
    ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (isAllowedDuringMaintenance(request) || !maintenanceService.isMaintenanceActive()) {
            filterChain.doFilter(request, response);
            return;
        }

        writeMaintenanceResponse(request, response);
    }

    private boolean isAllowedDuringMaintenance(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();

        return HttpMethod.OPTIONS.matches(method)
                || "/health".equals(path)
                || "/api/system/status".equals(path)
                || "/api/payments/sepay/webhook".equals(path)
                || path.startsWith("/api/admin/")
                || path.startsWith("/api/auth/")
                || path.startsWith("/ws/");
    }

    private void writeMaintenanceResponse(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ApiResponse<Object> body = ApiResponse.error(
                ErrorCode.MAINTENANCE_MODE,
                maintenanceService.getActiveMessage(),
                request.getRequestURI()
        );

        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
