package com.jaxon.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
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
            boolean csrfRequired = requiresCsrf(request);
            Optional<SessionPrincipal> resolved = authenticationService.resolveSession(
                    token.orElseThrow(), !csrfRequired);
            if (resolved.isEmpty()) {
                expireSessionCookie(response);
            } else {
                SessionPrincipal principal = resolved.orElseThrow();
                if (csrfRequired
                        && !AuthenticationService.csrfMatches(
                                principal.csrfToken(), request.getHeader("X-CSRF-Token"))) {
                    problemWriter.write(
                            request,
                            response,
                            HttpServletResponse.SC_FORBIDDEN,
                            "Solicitud no autorizada",
                            "El token CSRF no es válido.",
                            "CSRF_INVALID");
                    return;
                }
                if (csrfRequired) {
                    resolved = authenticationService.resolveSession(token.orElseThrow(), true);
                    if (resolved.isEmpty()) {
                        expireSessionCookie(response);
                    } else {
                        principal = resolved.orElseThrow();
                    }
                }
                if (resolved.isPresent()) {
                    authenticate(principal, token.orElseThrow());
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private static void authenticate(SessionPrincipal principal, String token) {
        List<SimpleGrantedAuthority> authorities = principal.roles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, token, authorities);
        authentication.setDetails(principal.userId());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static void expireSessionCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
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
