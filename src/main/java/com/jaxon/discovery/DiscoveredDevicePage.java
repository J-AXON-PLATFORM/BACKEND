package com.jaxon.discovery;

import java.util.List;

record DiscoveredDevicePage(
        List<DiscoveredDeviceResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {}
