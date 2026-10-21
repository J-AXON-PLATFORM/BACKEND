package com.jaxon.identity;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataAccessException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpMediaTypeNotSupportedException;

@RestControllerAdvice
class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiProblem> validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        ApiProblem base = ApiProblem.of(
                request,
                HttpStatus.BAD_REQUEST.value(),
                "Solicitud no válida",
                "Revisa los campos indicados.",
                "VALIDATION_FAILED");
        List<ApiProblem.FieldError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiProblem.FieldError(
                        "/" + error.getField(), "INVALID_FIELD", "El valor no es válido."))
                .toList();
        return response(new ApiProblem(
                base.type(),
                base.title(),
                base.status(),
                base.detail(),
                base.instance(),
                base.code(),
                base.correlationId(),
                errors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiProblem> malformed(HttpMessageNotReadableException exception, HttpServletRequest request) {
        return response(ApiProblem.of(
                request,
                HttpStatus.BAD_REQUEST.value(),
                "Solicitud mal formada",
                "El cuerpo JSON no es válido.",
                "MALFORMED_REQUEST"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiProblem> unsupportedMediaType(
            HttpMediaTypeNotSupportedException exception, HttpServletRequest request) {
        return response(ApiProblem.of(
                request,
                HttpStatus.BAD_REQUEST.value(),
                "Solicitud no válida",
                "La solicitud debe usar application/json.",
                "MALFORMED_REQUEST"));
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiProblem> unavailable(DataAccessException exception, HttpServletRequest request) {
        LOGGER.error("API persistence is unavailable", exception);
        return response(ApiProblem.of(
                request,
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                "Servicio no disponible",
                "No fue posible completar la solicitud.",
                "SERVICE_UNAVAILABLE"));
    }

    @ExceptionHandler(HumanUserService.UsernameConflictException.class)
    ResponseEntity<ApiProblem> usernameConflict(
            HumanUserService.UsernameConflictException exception, HttpServletRequest request) {
        return response(ApiProblem.of(
                request,
                HttpStatus.CONFLICT.value(),
                "Usuario existente",
                "Ya existe un usuario con ese nombre.",
                "USERNAME_CONFLICT"));
    }

    @ExceptionHandler({
        HumanUserService.UserValidationException.class,
        PasswordPolicy.PasswordPolicyException.class
    })
    ResponseEntity<ApiProblem> userValidation(RuntimeException exception, HttpServletRequest request) {
        return response(ApiProblem.of(
                request,
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                "Datos de usuario no válidos",
                "Revisa el usuario, la contraseña y los roles.",
                "USER_VALIDATION_FAILED"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiProblem> unexpected(Exception exception, HttpServletRequest request) {
        LOGGER.error("API request failed safely", exception);
        return response(ApiProblem.of(
                request,
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Error interno",
                "No fue posible completar la solicitud.",
                "INTERNAL_ERROR"));
    }

    private static ResponseEntity<ApiProblem> response(ApiProblem problem) {
        return ResponseEntity.status(problem.status())
                .header(HttpHeaders.CONTENT_TYPE, "application/problem+json")
                .body(problem);
    }
}
