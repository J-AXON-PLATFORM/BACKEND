package com.jaxon.support;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

record TicketCommentCreateRequest(@NotNull @Size(max = 10_000) String body) {}
