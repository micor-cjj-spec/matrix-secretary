package com.kailei.demo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kailei.demo.model.ApiErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-AI-Secretary-Key";
    private static final String BEARER_PREFIX = "Bearer ";

    private final String apiKey;
    private final ObjectMapper objectMapper;

    public ApiKeyAuthFilter(@Value("${ai-secretary.api-key:}") String apiKey,
                            ObjectMapper objectMapper) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (apiKey.isBlank()) {
            return true;
        }
        String path = request.getRequestURI();
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                || !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (isValidApiKey(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(
                response.getWriter(),
                ApiErrorResponse.of(
                        "UNAUTHORIZED",
                        "API Key 无效或缺失，请通过 X-AI-Secretary-Key 或 Authorization: Bearer <key> 传入有效凭证。",
                        request.getRequestURI(),
                        request.getHeader("X-Trace-Id")
                )
        );
    }

    private boolean isValidApiKey(HttpServletRequest request) {
        String headerKey = request.getHeader(API_KEY_HEADER);
        if (apiKey.equals(headerKey)) {
            return true;
        }

        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            return apiKey.equals(authorization.substring(BEARER_PREFIX.length()).trim());
        }
        return false;
    }
}
