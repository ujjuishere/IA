package com.example.demo.security;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OAuth2UserInfoService extends DefaultOAuth2UserService {

    private static final String GITHUB_EMAILS_ENDPOINT = "https://api.github.com/user/emails";

    private final RestClient restClient;

    public OAuth2UserInfoService() {
        this.restClient = RestClient.builder().build();
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        if (!"github".equalsIgnoreCase(registrationId)) {
            return oauth2User;
        }

        Map<String, Object> attributes = new LinkedHashMap<>(oauth2User.getAttributes());
        String email = readString(attributes.get("email"));

        if (email == null || email.isBlank()) {
            String accessToken = userRequest.getAccessToken().getTokenValue();
            String resolvedEmail = fetchGithubPrimaryEmail(accessToken);
            if (resolvedEmail != null && !resolvedEmail.isBlank()) {
                attributes.put("email", resolvedEmail);
            }
        }

        return new DefaultOAuth2User(oauth2User.getAuthorities(), attributes, "id");
    }

    private String fetchGithubPrimaryEmail(String accessToken) {
        try {
            List<Map<String, Object>> emails = restClient.get()
                    .uri(GITHUB_EMAILS_ENDPOINT)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            if (emails == null || emails.isEmpty()) {
                return null;
            }

            for (Map<String, Object> emailEntry : emails) {
                String email = readString(emailEntry.get("email"));
                boolean primary = Boolean.TRUE.equals(emailEntry.get("primary"));
                boolean verified = Boolean.TRUE.equals(emailEntry.get("verified"));
                if (primary && verified && email != null && !email.isBlank()) {
                    return email;
                }
            }

            for (Map<String, Object> emailEntry : emails) {
                String email = readString(emailEntry.get("email"));
                boolean verified = Boolean.TRUE.equals(emailEntry.get("verified"));
                if (verified && email != null && !email.isBlank()) {
                    return email;
                }
            }

            return readString(emails.get(0).get("email"));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String readString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
