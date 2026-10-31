package com.jaxon.ai;

import java.util.List;

record AiSourcePage(
        List<AiSourceResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {}
