package com.dearusback.identity.infrastructure.config;

import com.dearusback.identity.infrastructure.security.ApiAuthenticationEntryPoint;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {

    @Bean
    @Order(1)
    SecurityFilterChain publicAuthEndpoints(HttpSecurity http) throws Exception {
        return http
                .securityMatcher(request -> "POST".equals(request.getMethod()) && switch (
                        request.getRequestURI().substring(request.getContextPath().length())) {
                    case "/api/v1/auth/login", "/api/v1/auth/reissue", "/api/v1/auth/logout" -> true;
                    default -> false;
                })
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain protectedEndpoints(HttpSecurity http, ApiAuthenticationEntryPoint entryPoint) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(entryPoint))
                .oauth2ResourceServer(resource -> resource
                        .authenticationEntryPoint(entryPoint)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(token ->
                                new UsernamePasswordAuthenticationToken(
                                        Long.parseLong(token.getSubject()), null, List.of()
                                ))))
                .build();
    }
}
