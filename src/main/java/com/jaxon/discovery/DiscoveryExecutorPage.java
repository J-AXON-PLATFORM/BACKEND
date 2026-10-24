package com.jaxon.discovery;

import java.util.List;

record DiscoveryExecutorPage(
        List<DiscoveryExecutorResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {}
