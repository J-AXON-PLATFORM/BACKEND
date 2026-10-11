package com.jaxon.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
class SessionAuthenticationFilter extends OncePerRequestFilter {

    static final String COOKIE_NAME = "__Host-JAXON_SESSION";

    private final AuthenticationService authenticationService;
    private final ProblemWriter problemWriter;

    SessionAuthenticationFilter(AuthenticationService authenticationService, ProblemWriter problemWriter) {
        this.authenticationService = authenticationService;
        this.problemWriter = problemWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Optional<String> token = findCookie(request, COOKIE_NAME);
        if (token.isPresent()) {
            authenticationService.resolveSession(token.orElseThrow()).ifPresent(principal -> {
                List<SimpleGrantedAuthority> authorities = principal.roles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toList();
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, token.orElseThrow(), authorities));
            });
        }

        Object principal = SecurityContextHolder.getContext().getAuthentication() == null
                ? null
                : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof SessionPrincipal session
                && requiresCsrf(request)
                && !AuthenticationService.csrfMatches(session.csrfToken(), request.getHeader("X-CSRF-Token"))) {
            problemWriter.write(
                    request,
                    response,
                    HttpServletResponse.SC_FORBIDDEN,
                    "Solicitud no autorizada",
                    "El token CSRF no es válido.",
                    "CSRF_INVALID");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static boolean requiresCsrf(HttpServletRequest request) {
        return !request.getRequestURI().equals("/api/v1/auth/login")
                && !List.of(HttpMethod.GET.name(), HttpMethod.HEAD.name(), HttpMethod.OPTIONS.name())
                        .contains(request.getMethod());
    }

    private static Optional<String> findCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }
}
