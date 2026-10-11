package com.jaxon.identity;

import com.jaxon.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
class AuthenticationController {

    private final AuthenticationService authenticationService;

    AuthenticationController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/login")
    ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        UUID correlationId = UUID.fromString(MDC.get(CorrelationIdFilter.MDC_KEY));
        AuthenticationService.LoginResult result = authenticationService.login(
                request.username(), request.password(), httpRequest.getRemoteAddr(), correlationId);
        if (result.status() == AuthenticationService.LoginStatus.INVALID) {
            return problem(
                    httpRequest,
                    HttpStatus.UNAUTHORIZED,
                    "Credenciales no válidas",
                    "Las credenciales proporcionadas no son válidas.",
                    "AUTHENTICATION_FAILED");
        }
        if (result.status() == AuthenticationService.LoginStatus.RATE_LIMITED) {
            return problem(
                    httpRequest,
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Demasiados intentos",
                    "Espera antes de volver a intentarlo.",
                    "RATE_LIMIT_EXCEEDED");
        }

        ResponseCookie cookie = ResponseCookie.from(SessionAuthenticationFilter.COOKIE_NAME, result.sessionToken())
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(SessionResponse.from(result.principal()));
    }

    @GetMapping("/me")
    SessionResponse currentSession(@AuthenticationPrincipal SessionPrincipal principal) {
        return SessionResponse.from(principal);
    }

    private static ResponseEntity<ApiProblem> problem(
            HttpServletRequest request, HttpStatus status, String title, String detail, String code) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.CONTENT_TYPE, "application/problem+json")
                .body(ApiProblem.of(request, status.value(), title, detail, code));
    }
}
