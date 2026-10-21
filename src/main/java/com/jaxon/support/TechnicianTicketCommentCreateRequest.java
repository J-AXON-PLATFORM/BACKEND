package com.jaxon.support;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

record TechnicianTicketCommentCreateRequest(
        @NotNull @Pattern(regexp = "PUBLIC|INTERNAL") String visibility,
        @NotNull @Size(max = 10_000) String body) {}
