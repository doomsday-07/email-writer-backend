package com.email.writer.ratelimit;

import com.email.writer.error.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(50)
@Slf4j
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String API_PREFIX = "/api/";

    private final RateLimiter limiter;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String path = req.getRequestURI();
        if (path == null || !path.startsWith(API_PREFIX)) {
            chain.doFilter(req, res);
            return;
        }

        String key = resolveKey(req);
        if (!limiter.tryConsume(key)) {
            long retryAfter = limiter.getRetryAfterSeconds(key);
            log.warn("Rate limit exceeded for key={} on {}", key, path);
            res.setStatus(429);
            res.setHeader("Retry-After", String.valueOf(retryAfter));
            res.setContentType("application/json");
            ErrorResponse body = ErrorResponse.of(
                    429, "Too Many Requests",
                    "Rate limit exceeded. Try again in " + retryAfter + " seconds.",
                    path);
            objectMapper.writeValue(res.getOutputStream(), body);
            return;
        }
        chain.doFilter(req, res);
    }

    private String resolveKey(HttpServletRequest req) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof Jwt jwt) {
            return "user:" + jwt.getSubject();
        }
        String fwd = req.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            return "ip:" + fwd.split(",")[0].trim();
        }
        return "ip:" + req.getRemoteAddr();
    }
}
