package com.cacanode.api.platform.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PlatformSurfaceFilter extends OncePerRequestFilter {
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        boolean platformPath = path.startsWith("/api/v1/platform/");
        if (platformPath) response.setHeader("Cache-Control", "no-store");

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean authenticated = authentication != null && authentication.isAuthenticated();
        boolean platformAdmin = authenticated && authentication.getAuthorities().stream()
                .anyMatch(value -> value.getAuthority().equals("ROLE_PLATFORM_ADMIN"));
        if (authenticated && ((platformPath && !platformAdmin)
                || (platformAdmin && isCustomerAuthenticatedPath(path)))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), Map.of(
                    "timestamp", LocalDateTime.now().toString(), "status", 403,
                    "error", "Forbidden", "message", "This account cannot access the requested surface"));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isCustomerAuthenticatedPath(String path) {
        return path.startsWith("/api/")
                && !path.startsWith("/api/v1/platform/")
                && !path.startsWith("/api/v1/auth/")
                && !path.startsWith("/api/auth/")
                && !path.startsWith("/api/v1/public/")
                && !path.startsWith("/api/v1/widget/")
                && !path.startsWith("/api/v1/external/");
    }
}
