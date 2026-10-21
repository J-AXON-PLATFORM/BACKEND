package com.jaxon.identity;

import com.jaxon.CorrelationIdFilter;
import java.net.URI;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
class HumanUserController {

    private final HumanUserService humanUserService;

    HumanUserController(HumanUserService humanUserService) {
        this.humanUserService = humanUserService;
    }

    @PostMapping
    ResponseEntity<HumanUserResponse> create(
            @RequestBody HumanUserCreateRequest request,
            @AuthenticationPrincipal SessionPrincipal principal) {
        UUID correlationId = UUID.fromString(MDC.get(CorrelationIdFilter.MDC_KEY));
        HumanUserResponse response = humanUserService.create(request, principal.userId(), correlationId);
        return ResponseEntity.created(URI.create("/api/v1/users/" + response.userId())).body(response);
    }
}
