package com.jaxon.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
class ProblemWriter {

    private final ObjectMapper objectMapper;

    ProblemWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    void write(
            HttpServletRequest request,
            HttpServletResponse response,
            int status,
            String title,
            String detail,
            String code)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiProblem.of(request, status, title, detail, code));
    }
}
