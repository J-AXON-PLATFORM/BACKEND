package com.jaxon.audit_reporting;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

final class ReportRequestParser {

    static final int DEFAULT_SIZE = 50;
    static final int MAX_SIZE = 200;
    private static final Pattern UUID_V4 = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");
    private static final Pattern UTC_DATE_TIME = Pattern.compile(
            "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{1,9})?Z$");
    private static final Pattern NON_NEGATIVE_INTEGER = Pattern.compile("^(0|[1-9]\\d{0,8})$");
    private static final Pattern CODE = Pattern.compile("^[A-Z][A-Z0-9_]{0,63}$");

    private final Map<String, String[]> parameters;
    private final List<Error> errors = new ArrayList<>();

    ReportRequestParser(HttpServletRequest request, Set<String> allowed) {
        parameters = request.getParameterMap();
        for (Map.Entry<String, String[]> entry : parameters.entrySet()) {
            if (!allowed.contains(entry.getKey())) {
                errors.add(new Error("/query", "UNKNOWN_PARAMETER"));
                break;
            }
            if (entry.getValue().length != 1) {
                errors.add(new Error("/" + entry.getKey(), "DUPLICATE_PARAMETER"));
            }
        }
    }

    String member(String name, Set<String> allowed) {
        String raw = value(name);
        if (raw == null) return null;
        if (!raw.isEmpty() && allowed.contains(raw)) return raw;
        invalid(name);
        return null;
    }

    String code(String name) {
        String raw = value(name);
        if (raw == null) return null;
        if (CODE.matcher(raw).matches()) return raw;
        invalid(name);
        return null;
    }

    UUID uuid(String name) {
        String raw = value(name);
        if (raw == null) return null;
        if (UUID_V4.matcher(raw).matches()) return UUID.fromString(raw);
        invalid(name);
        return null;
    }

    Instant instant(String name) {
        String raw = value(name);
        if (raw == null) return null;
        try {
            if (UTC_DATE_TIME.matcher(raw).matches()) return Instant.parse(raw);
        } catch (DateTimeParseException exception) {
            // Reported below without echoing input.
        }
        invalid(name);
        return null;
    }

    int integer(String name, int fallback, int minimum, int maximum) {
        String raw = value(name);
        if (raw == null) return fallback;
        if (NON_NEGATIVE_INTEGER.matcher(raw).matches()) {
            int parsed = Integer.parseInt(raw);
            if (parsed >= minimum && parsed <= maximum) return parsed;
        }
        invalid(name);
        return fallback;
    }

    void range(Instant from, Instant to) {
        if (from != null && to != null && !from.isBefore(to)) {
            errors.add(new Error("/createdTo", "INVALID_RANGE"));
        }
    }

    List<Error> errors() {
        return List.copyOf(errors);
    }

    private String value(String name) {
        String[] values = parameters.get(name);
        return values == null || values.length != 1 ? null : values[0];
    }

    private void invalid(String name) {
        errors.add(new Error("/" + name, "INVALID_FIELD"));
    }

    record Error(String pointer, String code) {}
}
