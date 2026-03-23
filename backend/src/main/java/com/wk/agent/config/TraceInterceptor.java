package com.wk.agent.config;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;

@Component
public class TraceInterceptor implements HandlerInterceptor {

    private static final String TRACE_ID = "traceId";
    private static final String SPAN_ID = "spanId";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String traceId = request.getHeader(REQUEST_ID_HEADER);
        if (traceId == null || traceId.isEmpty()) {
            traceId = generateTraceId();
        }
        
        String spanId = generateSpanId();
        
        MDC.put(TRACE_ID, traceId);
        MDC.put(SPAN_ID, spanId);
        
        response.setHeader(REQUEST_ID_HEADER, traceId);
        
        request.setAttribute("startTime", System.currentTimeMillis());
        
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, 
                                 Object handler, Exception ex) {
        try {
            Long startTime = (Long) request.getAttribute("startTime");
            if (startTime != null) {
                long duration = System.currentTimeMillis() - startTime;
                MDC.put("duration", String.valueOf(duration));
            }
            
            MDC.put("status", String.valueOf(response.getStatus()));
            MDC.put("method", request.getMethod());
            MDC.put("uri", request.getRequestURI());
            
        } finally {
            MDC.remove(TRACE_ID);
            MDC.remove(SPAN_ID);
            MDC.remove("duration");
            MDC.remove("status");
            MDC.remove("method");
            MDC.remove("uri");
        }
    }

    private String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private String generateSpanId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
