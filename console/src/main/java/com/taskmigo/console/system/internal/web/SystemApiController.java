package com.taskmigo.console.system.internal.web;

import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = {"/api"})
public class SystemApiController {
  @GetMapping(value = {"/public"})
  public Map<String, String> publicEndpoint() {
    return Map.of("message", "Taskmigo Console is running");
  }

  @GetMapping(value = {"/me"})
  @PreAuthorize(value = "hasAuthority('SCOPE_api.read')")
  public Map<String, Object> currentUser(@AuthenticationPrincipal Jwt jwt) {
    return Map.of("subject", jwt.getSubject(), "scopes", jwt.getClaimAsStringList("scope"));
  }

  @GetMapping(value = {"/admin"})
  @PreAuthorize(value = "hasAuthority('SCOPE_api.admin')")
  public Map<String, String> adminEndpoint(@AuthenticationPrincipal Jwt jwt) {
    return Map.of("message", "admin access granted", "subject", jwt.getSubject());
  }
}
