package com.jaxon.automation;

import java.util.List;

record NotificationPage(
        List<NotificationResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {}
