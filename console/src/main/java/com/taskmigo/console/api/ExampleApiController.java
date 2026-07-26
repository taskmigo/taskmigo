package com.taskmigo.console.api;

import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ExampleApiController {

    @GetMapping("/public")
    Map<String, String> publicEndpoint() {
        return Map.of("message", "Taskmigo Console is running");
    }

    @GetMapping("/me")
    Map<String, Object> currentUser(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("subject", jwt.getSubject(), "scopes", jwt.getClaimAsStringList("scope"));
    }

    @GetMapping("/admin")
    Map<String, String> adminEndpoint(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("message", "admin access granted", "subject", jwt.getSubject());
    }
}
