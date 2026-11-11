package com.thanh.workhub.platform.web;

import java.io.IOException;
import java.util.UUID;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {
    public static final String ATTRIBUTE = TraceIdFilter.class.getName() + ".traceId";

    public static String traceId(HttpServletRequest request) {
        String traceId = (String) request.getAttribute(ATTRIBUTE);
        if (traceId == null) {
            traceId = UUID.randomUUID().toString();
            request.setAttribute(ATTRIBUTE, traceId);
        }
        return traceId;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String traceId = traceId(request);
        response.setHeader("X-Trace-Id", traceId);
        String previous = MDC.get("traceId");
        MDC.put("traceId", traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            if (previous == null) MDC.remove("traceId");
            else MDC.put("traceId", previous);
        }
    }
}
