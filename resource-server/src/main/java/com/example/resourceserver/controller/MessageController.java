package com.example.resourceserver.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class MessageController {

    @GetMapping("/message")
    public Map<String, Object> getMessage(@AuthenticationPrincipal Jwt jwt) {
        return Map.of(
            "message", "Bonjour depuis le Resource Server !",
            "subject", jwt.getClaimAsString("sub"),
            "preferred_username", String.valueOf(jwt.getClaimAsString("preferred_username")),
            "scope", String.valueOf(jwt.getClaimAsString("scope")),
            "roles", extractRoles(jwt)
        );
    }

    @GetMapping("/user/profile")
    public Map<String, Object> userProfile(@AuthenticationPrincipal Jwt jwt) {
        return Map.of(
            "message", "Zone UTILISATEUR — accessible à tout utilisateur authentifié (rôle USER ou ADMIN).",
            "user", String.valueOf(jwt.getClaimAsString("preferred_username")),
            "roles", extractRoles(jwt)
        );
    }

    @GetMapping("/admin/dashboard")
    public Map<String, Object> adminDashboard(@AuthenticationPrincipal Jwt jwt) {
        return Map.of(
            "message", "Zone ADMIN — données sensibles réservées aux administrateurs.",
            "user", String.valueOf(jwt.getClaimAsString("preferred_username")),
            "secret", "🔒 nombre d'utilisateurs en base : 42",
            "roles", extractRoles(jwt)
        );
    }

    @GetMapping("/public/hello")
    public Map<String, String> publicHello() {
        return Map.of("message", "Endpoint public - pas besoin de token");
    }

    @SuppressWarnings("unchecked")
    private static List<String> extractRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null) {
            return List.of();
        }
        Object roles = realmAccess.get("roles");
        if (roles instanceof List<?> list) {
            return (List<String>) list;
        }
        return List.of();
    }
}
