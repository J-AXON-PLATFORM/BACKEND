package com.jaxon.support;

import java.util.List;

record TicketPage(
        List<TicketResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {}
