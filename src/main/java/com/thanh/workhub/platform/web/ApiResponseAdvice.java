package com.thanh.workhub.platform.web;

import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.*;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice
public class ApiResponseAdvice implements ResponseBodyAdvice<Object> {
    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
            Class<? extends HttpMessageConverter<?>> converterType,
            ServerHttpRequest request, ServerHttpResponse response) {
        if (!request.getURI().getPath().startsWith("/api/") || body instanceof ApiResponse<?>) return body;
        var servletRequest = ((ServletServerHttpRequest) request).getServletRequest();
        int status = ((ServletServerHttpResponse) response).getServletResponse().getStatus();
        if (status == 204 || status == 304) return body;
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        if (body instanceof ProblemDetail problem) {
            return new ApiResponse<>(problem.getStatus(), problem.getStatus(),
                    problem.getDetail(), null, TraceIdFilter.traceId(servletRequest));
        }
        return new ApiResponse<>(status >= 400 ? status : 0, status,
                status >= 400 ? "Yêu cầu thất bại" : "Thành công", body,
                TraceIdFilter.traceId(servletRequest));
    }
}
