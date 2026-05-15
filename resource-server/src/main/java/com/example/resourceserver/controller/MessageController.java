package com.example.resourceserver.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class MessageController {

    @GetMapping("/message")
    public Map<String, String> getMessage(@AuthenticationPrincipal Jwt jwt) {
        String clientId = jwt.getClaimAsString("sub");
        return Map.of(
            "message", "Bonjour depuis le Resource Server !",
            "client", clientId,
            "scope", jwt.getClaimAsString("scope")
        );
    }

    @GetMapping("/public/hello")
    public Map<String, String> publicHello() {
        return Map.of("message", "Endpoint public - pas besoin de token");
    }
}
