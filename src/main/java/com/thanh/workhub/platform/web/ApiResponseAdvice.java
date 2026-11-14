package com.thanh.workhub.platform.web;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/** Bọc mọi response JSON dưới /api/** vào {@link ApiResponse}. */
@RestControllerAdvice
public class ApiResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // StringHttpMessageConverter chỉ ghi được String; bọc object vào đó sẽ gây ClassCastException.
        return !StringHttpMessageConverter.class.isAssignableFrom(converterType);
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
            Class<? extends HttpMessageConverter<?>> converterType,
            ServerHttpRequest request, ServerHttpResponse response) {
        if (!request.getURI().getPath().startsWith("/api/") || body instanceof ApiResponse<?>) return body;
        var servletRequest = ((ServletServerHttpRequest) request).getServletRequest();
        int status = ((ServletServerHttpResponse) response).getServletResponse().getStatus();
        if (status == 204 || status == 304) return body;
        String traceId = TraceIdFilter.traceId(servletRequest);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        if (body instanceof ProblemDetail problem) {
            return ApiResponse.error(problem.getStatus(), problem.getStatus(), problem.getDetail(), traceId);
        }
        boolean failed = status >= 400;
        return new ApiResponse<>(failed ? status : 0, status,
                failed ? "Yêu cầu thất bại" : "Thành công", body, traceId);
    }
}
