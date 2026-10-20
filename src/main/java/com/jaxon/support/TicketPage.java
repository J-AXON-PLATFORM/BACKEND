package com.jaxon.support;

import java.util.List;

record TicketPage(
        List<TicketListItemResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {}
