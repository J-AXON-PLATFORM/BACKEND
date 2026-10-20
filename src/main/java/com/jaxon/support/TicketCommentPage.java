package com.jaxon.support;

import java.util.List;

record TicketCommentPage(
        List<TicketCommentResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {}
