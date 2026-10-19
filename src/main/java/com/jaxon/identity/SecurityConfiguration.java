package com.jaxon.identity;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SessionAuthenticationFilter sessionAuthenticationFilter,
            ProblemWriter problemWriter)
            throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .formLogin(login -> login.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> problemWriter.write(
                                request,
                                response,
                                HttpServletResponse.SC_UNAUTHORIZED,
                                "Autenticación requerida",
                                "Se requiere una sesión válida.",
                                "AUTHENTICATION_REQUIRED"))
                        .accessDeniedHandler((request, response, exception) -> problemWriter.write(
                                request,
                                response,
                                HttpServletResponse.SC_FORBIDDEN,
                                "Acceso denegado",
                                "La sesión no autoriza la operación.",
                                "ACCESS_DENIED")))
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/prometheus",
                                "/error")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/assets").hasRole("IT_OPERATOR")
                        .requestMatchers(HttpMethod.GET, "/api/v1/assets/*/technical-state")
                        .hasRole("IT_OPERATOR")
                        .requestMatchers(HttpMethod.GET, "/api/v1/assets/*/technical-state/events")
                        .hasRole("IT_OPERATOR")
                        .requestMatchers(HttpMethod.GET, "/api/v1/assets/*").hasRole("IT_OPERATOR")
                        .requestMatchers(HttpMethod.POST, "/api/v1/agents").hasRole("IT_OPERATOR")
                        .requestMatchers(HttpMethod.GET, "/api/v1/agents/*").hasRole("IT_OPERATOR")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/agents/*/ssh-key").hasRole("IT_OPERATOR")
                        .requestMatchers(HttpMethod.POST, "/api/v1/agents/*/activate").hasRole("IT_OPERATOR")
                        .requestMatchers(HttpMethod.POST, "/api/v1/agents/*/tasks").hasRole("IT_OPERATOR")
                        .requestMatchers(HttpMethod.GET, "/api/v1/agents/*/tasks/*").hasRole("IT_OPERATOR")
                        .requestMatchers(HttpMethod.POST, "/api/v1/tickets").hasRole("IT_OPERATOR")
                        .requestMatchers(HttpMethod.GET, "/api/v1/tickets/*").hasRole("IT_OPERATOR")
                        .anyRequest().denyAll())
                .addFilterBefore(sessionAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${jaxon.identity.frontend-origin}") String frontendOrigin) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(frontendOrigin));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "X-CSRF-Token", "X-Correlation-ID"));
        configuration.setExposedHeaders(List.of("X-Correlation-ID"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
