package com.example.frontend.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Controller
public class HomeController {

    private final RestClient restClient;

    public HomeController(RestClient restClient) {
        this.restClient = restClient;
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/home")
    public String home(@AuthenticationPrincipal OidcUser oidcUser, Model model) {
        model.addAttribute("user", oidcUser);
        model.addAttribute("claims", oidcUser.getClaims());
        model.addAttribute("idToken", oidcUser.getIdToken().getTokenValue());
        return "home";
    }

    @GetMapping("/fragments/message")
    public String message(@RegisteredOAuth2AuthorizedClient("keycloak") OAuth2AuthorizedClient authorizedClient,
                          Model model) {
        String accessToken = authorizedClient.getAccessToken().getTokenValue();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.get()
                    .uri("http://localhost:8081/api/message")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(Map.class);
            model.addAttribute("response", response);
            model.addAttribute("error", null);
        } catch (Exception e) {
            model.addAttribute("response", null);
            model.addAttribute("error", e.getMessage());
        }
        return "fragments/message :: card";
    }

    @GetMapping("/fragments/token")
    public String token(@RegisteredOAuth2AuthorizedClient("keycloak") OAuth2AuthorizedClient authorizedClient,
                        Model model) {
        model.addAttribute("accessToken", authorizedClient.getAccessToken().getTokenValue());
        model.addAttribute("expiresAt", authorizedClient.getAccessToken().getExpiresAt());
        model.addAttribute("scopes", authorizedClient.getAccessToken().getScopes());
        return "fragments/token :: card";
    }
}
