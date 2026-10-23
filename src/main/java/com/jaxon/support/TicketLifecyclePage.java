package com.jaxon.support;

import java.util.List;

record TicketLifecyclePage(
        List<TicketLifecycleEntryResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {}
