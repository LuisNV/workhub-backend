
package com.thanh.workhub.platform.security;

import java.util.Base64;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

@Configuration
public class SecurityConfig {

    // 1. Băm và kiểm tra mật khẩu
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    // 2. Tạo khóa dùng để ký và kiểm tra JWT
    @Bean
    public SecretKey jwtSecretKey(
            @Value("${app.jwt.secret-base64}") String base64Secret
    ) {
        byte[] keyBytes = Base64.getDecoder()
                .decode(base64Secret);

        if (keyBytes.length < 32) {
            throw new IllegalArgumentException(
                "JWT secret must be at least 32 bytes"
            );
        }

        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    // 3. Dùng để phát JWT
    @Bean
    public JwtEncoder jwtEncoder(SecretKey key) {
        JWKSource<SecurityContext> source =
                new ImmutableSecret<>(key);

        return new NimbusJwtEncoder(source);
    }

    // 4. Dùng để xác minh JWT
    @Bean
    public JwtDecoder jwtDecoder(
            SecretKey key,
            @Value("${app.jwt.issuer}") String issuer
    ) {
        NimbusJwtDecoder decoder =
                NimbusJwtDecoder.withSecretKey(key)
                    .macAlgorithm(MacAlgorithm.HS256)
                    .build();

        decoder.setJwtValidator(
            JwtValidators.createDefaultWithIssuer(issuer)
        );

        return decoder;
    }

    // 5. Bảo vệ các API
    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origin-patterns}") List<String> allowedOriginPatterns
    ) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(allowedOriginPatterns);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        configuration.setExposedHeaders(List.of("X-Trace-Id"));
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper
    ) throws Exception {

        return http
            .cors(Customizer.withDefaults())
            .csrf(AbstractHttpConfigurer::disable)

            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)

            .sessionManagement(session -> session
                .sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS
                )
            )

            .authorizeHttpRequests(auth -> auth

                // API login không cần token
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/v1/auth/login",
                    "/api/v1/auth/refresh",
                    "/api/v1/auth/logout"
                ).permitAll()

                // Health check công khai
                .requestMatchers(
                    HttpMethod.GET,
                    "/actuator/health"
                ).permitAll()

                // API còn lại cần xác thực
                .anyRequest().authenticated()
            )

            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) ->
                    writeError(objectMapper, request, response, 401, "Bạn chưa đăng nhập hoặc token không hợp lệ"))
                .accessDeniedHandler((request, response, exception) ->
                    writeError(objectMapper, request, response, 403, "Bạn không có quyền truy cập"))
            )

            // Spring Security tự xử lý Bearer JWT
            .oauth2ResourceServer(oauth ->
                oauth.jwt(Customizer.withDefaults())
                    .authenticationEntryPoint((request, response, exception) ->
                        writeError(objectMapper, request, response, 401, "Bạn chưa đăng nhập hoặc token không hợp lệ"))
                    .accessDeniedHandler((request, response, exception) ->
                        writeError(objectMapper, request, response, 403, "Bạn không có quyền truy cập"))
            )

            .build();
    }
    private static void writeError(com.fasterxml.jackson.databind.ObjectMapper mapper,
            jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response, int status, String message)
            throws java.io.IOException {
        response.setStatus(status);
        if (status == 401) response.setHeader("WWW-Authenticate", "Bearer");
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(), new com.thanh.workhub.platform.web.ApiResponse<>(
                status, status, message, null,
                com.thanh.workhub.platform.web.TraceIdFilter.traceId(request)));
    }
}
