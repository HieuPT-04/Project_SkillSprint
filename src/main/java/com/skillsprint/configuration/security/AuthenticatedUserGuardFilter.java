package com.skillsprint.configuration.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsprint.common.ApiResponse;
import com.skillsprint.enums.auth.UserStatus;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.repository.UserRepository;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthenticatedUserGuardFilter extends OncePerRequestFilter {

    UserRepository userRepository;
    ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (isSkippedPath(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            filterChain.doFilter(request, response);
            return;
        }

        String userId = jwtAuthentication.getToken().getSubject();
        var user = userRepository.findById(userId);
        if (user.isEmpty()) {
            writeErrorResponse(request, response, ErrorCode.USER_CONTEXT_INVALID);
            return;
        }
        if (UserStatus.DISABLED.equals(user.get().getStatus())) {
            writeErrorResponse(request, response, ErrorCode.ACCOUNT_DISABLED);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isSkippedPath(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();

        return HttpMethod.OPTIONS.matches(method)
                || "/health".equals(path)
                || "/api/system/status".equals(path)
                || "/api/subscriptions/plans".equals(path)
                || "/api/payments/sepay/webhook".equals(path)
                || path.startsWith("/api/auth/")
                || path.startsWith("/ws/");
    }

    private void writeErrorResponse(
            HttpServletRequest request,
            HttpServletResponse response,
            ErrorCode errorCode
    ) throws IOException {
        ApiResponse<Object> body = ApiResponse.error(errorCode, request.getRequestURI());

        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
