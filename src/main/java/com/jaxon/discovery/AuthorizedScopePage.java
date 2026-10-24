package com.jaxon.discovery;

import java.util.List;

record AuthorizedScopePage(
        List<AuthorizedScopeResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {}
