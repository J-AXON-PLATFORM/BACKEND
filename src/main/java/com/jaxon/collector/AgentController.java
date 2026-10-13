package com.jaxon.collector;

import com.jaxon.CorrelationIdFilter;
import com.jaxon.identity.AgentIdentityRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agents")
class AgentController {

    static final String UUID_V4_PATTERN =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$";
    private static final Pattern UUID_V4 = Pattern.compile(UUID_V4_PATTERN);

    private final AgentService agentService;

    AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping
    ResponseEntity<AgentResponse> create(
            @Valid @RequestBody AgentCreateRequest request, Authentication authentication) {
        AgentResponse created = agentService.create(request, actorId(authentication), correlationId());
        return ResponseEntity.created(URI.create("/api/v1/agents/" + created.agentId())).body(created);
    }

    @GetMapping("/{agentId}")
    ResponseEntity<?> find(@PathVariable String agentId, HttpServletRequest request) {
        if (!UUID_V4.matcher(agentId).matches()) {
            return notFound(request);
        }
        return agentService.find(UUID.fromString(agentId))
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> notFound(request));
    }

    @PutMapping("/{agentId}/ssh-key")
    ResponseEntity<?> registerPublicKey(
            @PathVariable String agentId,
            @Valid @RequestBody AgentSshKeyRequest request,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        if (!UUID_V4.matcher(agentId).matches()) {
            return notFound(servletRequest);
        }
        return ResponseEntity.ok(agentService.registerPublicKey(
                UUID.fromString(agentId), request, actorId(authentication), correlationId()));
    }

    @PostMapping("/{agentId}/activate")
    ResponseEntity<?> activate(
            @PathVariable String agentId,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        if (!UUID_V4.matcher(agentId).matches()) {
            return notFound(servletRequest);
        }
        return ResponseEntity.ok(agentService.activate(
                UUID.fromString(agentId), actorId(authentication), correlationId()));
    }

    @ExceptionHandler({AssetNotFoundException.class})
    ResponseEntity<AgentProblem> assetNotFound(RuntimeException exception, HttpServletRequest request) {
        return problem(
                request,
                HttpStatus.NOT_FOUND,
                "Activo no encontrado",
                "No existe un activo con el identificador indicado.",
                "ASSET_NOT_FOUND");
    }

    @ExceptionHandler({AgentNotFoundException.class})
    ResponseEntity<AgentProblem> agentNotFound(RuntimeException exception, HttpServletRequest request) {
        return notFound(request);
    }

    @ExceptionHandler(AgentIdentityRegistry.InvalidPublicKeyException.class)
    ResponseEntity<AgentProblem> invalidPublicKey(RuntimeException exception, HttpServletRequest request) {
        return problem(
                request,
                HttpStatus.UNPROCESSABLE_CONTENT,
                "Clave pública no válida",
                "La clave debe ser Ed25519 válida en formato OpenSSH.",
                "VALIDATION_FAILED");
    }

    @ExceptionHandler(AgentIdentityRegistry.PublicKeyConflictException.class)
    ResponseEntity<AgentProblem> publicKeyConflict(RuntimeException exception, HttpServletRequest request) {
        return problem(
                request,
                HttpStatus.CONFLICT,
                "Conflicto de clave pública",
                "La clave no puede asociarse al Agent indicado.",
                "AGENT_KEY_CONFLICT");
    }

    @ExceptionHandler(AgentPublicKeyRequiredException.class)
    ResponseEntity<AgentProblem> publicKeyRequired(RuntimeException exception, HttpServletRequest request) {
        return problem(request, HttpStatus.CONFLICT, "Clave pública requerida",
                "El Agent debe tener una clave pública registrada.", "AGENT_PUBLIC_KEY_REQUIRED");
    }

    @ExceptionHandler(AssetNotActiveException.class)
    ResponseEntity<AgentProblem> assetNotActive(RuntimeException exception, HttpServletRequest request) {
        return problem(request, HttpStatus.CONFLICT, "Activo no disponible",
                "El activo debe estar ACTIVE para activar su Agent.", "ASSET_NOT_ACTIVE");
    }

    @ExceptionHandler({AgentStateConflictException.class, AssetActiveAgentConflictException.class})
    ResponseEntity<AgentProblem> activationConflict(RuntimeException exception, HttpServletRequest request) {
        String code = exception instanceof AssetActiveAgentConflictException
                ? "ASSET_ACTIVE_AGENT_CONFLICT"
                : "AGENT_STATE_CONFLICT";
        return problem(request, HttpStatus.CONFLICT, "Conflicto de activación",
                "El Agent no puede activarse en su estado actual.", code);
    }

    @ExceptionHandler(SftpProvisioningConflictException.class)
    ResponseEntity<AgentProblem> provisioningConflict(RuntimeException exception, HttpServletRequest request) {
        return problem(request, HttpStatus.CONFLICT, "Conflicto de aprovisionamiento",
                "El buzón SFTP existente no coincide con el Agent.", "SFTP_PROVISIONING_CONFLICT");
    }

    @ExceptionHandler({SftpProvisioningUnavailableException.class, SftpHostKeyMismatchException.class})
    ResponseEntity<AgentProblem> provisioningUnavailable(RuntimeException exception, HttpServletRequest request) {
        String code = exception instanceof SftpHostKeyMismatchException
                ? "SFTP_HOST_KEY_MISMATCH"
                : "SFTP_PROVISIONING_UNAVAILABLE";
        return problem(request, HttpStatus.SERVICE_UNAVAILABLE, "SFTP no disponible",
                "No se pudo confirmar el aprovisionamiento SFTP de forma segura.", code);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<AgentProblem> validation(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<FieldError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldError(
                        "/" + error.getField(), "INVALID_FIELD", "El valor no es válido."))
                .toList();
        return problem(
                request,
                HttpStatus.UNPROCESSABLE_CONTENT,
                "Validación fallida",
                "Revisa los campos indicados.",
                "VALIDATION_FAILED",
                errors);
    }

    private static UUID actorId(Authentication authentication) {
        return (UUID) authentication.getDetails();
    }

    private static UUID correlationId() {
        return UUID.fromString(MDC.get(CorrelationIdFilter.MDC_KEY));
    }

    private static ResponseEntity<AgentProblem> notFound(HttpServletRequest request) {
        return problem(
                request,
                HttpStatus.NOT_FOUND,
                "Agent no encontrado",
                "No existe un Agent con el identificador indicado.",
                "AGENT_NOT_FOUND");
    }

    private static ResponseEntity<AgentProblem> problem(
            HttpServletRequest request, HttpStatus status, String title, String detail, String code) {
        return problem(request, status, title, detail, code, List.of());
    }

    private static ResponseEntity<AgentProblem> problem(
            HttpServletRequest request,
            HttpStatus status,
            String title,
            String detail,
            String code,
            List<FieldError> errors) {
        AgentProblem body = new AgentProblem(
                "https://problems.j-axon.invalid/" + code.toLowerCase().replace('_', '-'),
                title,
                status.value(),
                detail,
                request.getRequestURI(),
                code,
                correlationId(),
                errors);
        return ResponseEntity.status(status)
                .header(HttpHeaders.CONTENT_TYPE, "application/problem+json")
                .body(body);
    }

    record AgentProblem(
            String type,
            String title,
            int status,
            String detail,
            String instance,
            String code,
            UUID correlationId,
            List<FieldError> errors) {}

    record FieldError(String pointer, String code, String message) {}
}
