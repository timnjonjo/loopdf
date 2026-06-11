// filter/RequestLoggingFilter.java
package com.loopdfs.rdas.filters;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Logs every inbound request and its response status.
 * Assigns a correlation ID for distributed tracing.
 */
@Slf4j
@Component
@Order(1)
public class RequestLoggingFilter implements Filter {

    private static final String CORRELATION_HEADER = "X-Correlation-ID";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  httpReq  = (HttpServletRequest)  request;
        HttpServletResponse httpResp = (HttpServletResponse) response;

        String correlationId = httpReq.getHeader(CORRELATION_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        httpResp.setHeader(CORRELATION_HEADER, correlationId);

        long start = System.currentTimeMillis();
        log.info("[{}] --> {} {}", correlationId, httpReq.getMethod(), httpReq.getRequestURI());

        chain.doFilter(request, response);

        log.info("[{}] <-- {} {} ({}ms)",
                correlationId, httpReq.getMethod(), httpReq.getRequestURI(),
                System.currentTimeMillis() - start);
    }
}