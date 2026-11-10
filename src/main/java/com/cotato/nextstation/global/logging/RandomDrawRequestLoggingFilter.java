package com.cotato.nextstation.global.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RandomDrawRequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().equals(request.getContextPath() + "/api/v1/random");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        long startedAt = System.nanoTime();
        log.info("랜덤뽑기 요청 시작 - requestId={}, method={}", requestId, request.getMethod());

        try {
            chain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException ex) {
            log.warn("랜덤뽑기 요청 중단 - requestId={}, exception={}, elapsedMs={}",
                    requestId, ex.getClass().getSimpleName(), elapsedMillis(startedAt));
            throw ex;
        }

        log.info("랜덤뽑기 요청 처리 종료 - requestId={}, status={}, elapsedMs={}",
                requestId, response.getStatus(), elapsedMillis(startedAt));
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }
}
