package com.jaxon.identity;

import com.jaxon.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;

record ApiProblem(
        String type,
        String title,
        int status,
        String detail,
        String instance,
        String code,
        UUID correlationId,
        List<FieldError> errors) {

    static ApiProblem of(HttpServletRequest request, int status, String title, String detail, String code) {
        String value = MDC.get(CorrelationIdFilter.MDC_KEY);
        UUID correlationId = value == null ? UUID.randomUUID() : UUID.fromString(value);
        return new ApiProblem(
                "https://problems.j-axon.invalid/" + code.toLowerCase().replace('_', '-'),
                title,
                status,
                detail,
                request.getRequestURI(),
                code,
                correlationId,
                List.of());
    }

    record FieldError(String pointer, String code, String message) {}
}
