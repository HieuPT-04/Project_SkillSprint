package com.skillsprint.configuration.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsprint.common.ApiResponse;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.service.auth.UserSessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SingleSessionFilter extends OncePerRequestFilter {

    UserSessionService userSessionService;
    ObjectMapper objectMapper;

    @Value("${app.session.header-name:X-Session-Id}")
    @NonFinal
    String sessionHeaderName;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if ("/api/auth/oauth/session".equals(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            filterChain.doFilter(request, response);
            return;
        }

        String userId = jwtAuthentication.getToken().getSubject();
        String sessionId = request.getHeader(sessionHeaderName);
        if (!userSessionService.isCurrentSession(userId, sessionId)) {
            writeUnauthorizedResponse(request, response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeUnauthorizedResponse(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ApiResponse<Object> body = ApiResponse.error(ErrorCode.SESSION_EXPIRED, request.getRequestURI());

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
