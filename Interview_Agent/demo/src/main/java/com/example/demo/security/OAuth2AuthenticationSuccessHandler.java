package com.example.demo.security;

import com.example.demo.entity.AuthProvider;
import com.example.demo.model.AuthResponse;
import com.example.demo.service.AuthService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Map;

@Component
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final AuthService authService;
    private final String successRedirectUrl;

    public OAuth2AuthenticationSuccessHandler(
            AuthService authService,
            @Value("${app.oauth.success-redirect-url}") String successRedirectUrl
    ) {
        this.authService = authService;
        this.successRedirectUrl = successRedirectUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {

        OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
        String registrationId = token.getAuthorizedClientRegistrationId();
        OAuth2User oauth2User = token.getPrincipal();

        AuthProvider provider = "google".equalsIgnoreCase(registrationId) ? AuthProvider.GOOGLE : AuthProvider.GITHUB;
        Map<String, Object> attributes = oauth2User.getAttributes();

        String providerUserId = readString(attributes, "sub");
        if (provider == AuthProvider.GITHUB) {
            providerUserId = providerUserId == null ? readString(attributes, "id") : providerUserId;
        }

        String email = readString(attributes, "email");
        String name = readString(attributes, "name");
        if (name == null || name.isBlank()) {
            name = readString(attributes, "login");
        }

        AuthResponse authResponse = authService.loginOrRegisterOAuthUser(provider, providerUserId, email, name);

        String targetUrl = UriComponentsBuilder
                .fromUriString(successRedirectUrl)
                .queryParam("token", authResponse.getToken())
                .queryParam("provider", authResponse.getProvider())
                .build()
                .toUriString();

        clearAuthenticationAttributes(request);
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    private String readString(Map<String, Object> attributes, String key) {
        Object value = attributes.get(key);
        return value == null ? null : String.valueOf(value);
    }
}
