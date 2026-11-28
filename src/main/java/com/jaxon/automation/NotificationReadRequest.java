package com.jaxon.automation;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

record NotificationReadRequest(@NotNull @Min(0) Long expectedVersion) {}
