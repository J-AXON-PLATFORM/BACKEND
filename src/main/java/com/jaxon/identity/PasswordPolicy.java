package com.jaxon.identity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
class PasswordPolicy {

    private final Set<String> blocked;

    PasswordPolicy() throws IOException {
        try (var input = new ClassPathResource("identity/password-blocklist.txt").getInputStream()) {
            blocked = new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .lines()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty())
                    .map(value -> value.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toUnmodifiableSet());
        }
    }

    void validate(String password) {
        if (password == null || password.length() < 15 || password.length() > 256) {
            throw new IllegalStateException("Bootstrap password must contain between 15 and 256 characters");
        }
        if (blocked.contains(password.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException("Bootstrap password is present in the local blocklist");
        }
    }
}
