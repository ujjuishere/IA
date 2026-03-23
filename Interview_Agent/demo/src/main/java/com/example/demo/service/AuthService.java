package com.example.demo.service;

import com.example.demo.entity.AppUser;
import com.example.demo.entity.AuthProvider;
import com.example.demo.model.AuthRequest;
import com.example.demo.model.AuthResponse;
import com.example.demo.model.SignupRequest;
import com.example.demo.model.UserProfileResponse;
import com.example.demo.repository.AppUserRepository;
import com.example.demo.security.JwtService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;

@Service
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponse signup(SignupRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        if (appUserRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("Email is already registered");
        }

        AppUser user = new AppUser();
        user.setEmail(normalizedEmail);
        user.setDisplayName(request.getName().trim());
        user.setProvider(AuthProvider.LOCAL);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));

        AppUser saved = appUserRepository.save(user);
        return buildAuthResponse(saved);
    }

    public AuthResponse signin(AuthRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        AppUser user = appUserRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        if (user.getProvider() != AuthProvider.LOCAL) {
            throw new IllegalArgumentException("This account uses social login. Please continue with Google/GitHub.");
        }

        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        return buildAuthResponse(user);
    }

    public AuthResponse loginOrRegisterOAuthUser(AuthProvider provider, String providerUserId, String email, String displayName) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("OAuth provider did not return an email");
        }

        String normalizedEmail = email.trim().toLowerCase();

        AppUser user = appUserRepository.findByEmail(normalizedEmail)
                .map(existing -> {
                    if (existing.getProvider() == AuthProvider.LOCAL) {
                        return existing;
                    }
                    existing.setProvider(provider);
                    existing.setProviderUserId(providerUserId);
                    existing.setDisplayName(displayName == null || displayName.isBlank() ? existing.getDisplayName() : displayName);
                    return appUserRepository.save(existing);
                })
                .orElseGet(() -> {
                    AppUser created = new AppUser();
                    created.setEmail(normalizedEmail);
                    created.setDisplayName(displayName == null || displayName.isBlank() ? normalizedEmail : displayName.trim());
                    created.setProvider(provider);
                    created.setProviderUserId(providerUserId);
                    return appUserRepository.save(created);
                });

        return buildAuthResponse(user);
    }

    public UserProfileResponse me(String email) {
        AppUser user = appUserRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        return toProfile(user);
    }

    public UserProfileResponse meOAuth(String registrationId, Map<String, Object> attributes) {
        AuthProvider provider = "google".equalsIgnoreCase(registrationId) ? AuthProvider.GOOGLE : AuthProvider.GITHUB;

        String email = readString(attributes.get("email"));
        String displayName = readString(attributes.get("name"));
        if (displayName == null || displayName.isBlank()) {
            displayName = readString(attributes.get("login"));
        }

        String providerUserId = provider == AuthProvider.GOOGLE
                ? readString(attributes.get("sub"))
                : readString(attributes.get("id"));

        AppUser user = null;
        if (email != null && !email.isBlank()) {
            user = appUserRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT)).orElse(null);
        }

        if (user == null && providerUserId != null && !providerUserId.isBlank()) {
            user = appUserRepository.findByProviderAndProviderUserId(provider, providerUserId).orElse(null);
        }

        if (user == null) {
            AuthResponse response = loginOrRegisterOAuthUser(provider, providerUserId, email, displayName);
            user = appUserRepository.findById(response.getUserId())
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
        }

        return toProfile(user);
    }

    private AuthResponse buildAuthResponse(AppUser user) {
        UserDetails principal = User.withUsername(user.getEmail())
                .password(user.getPasswordHash() == null ? "" : user.getPasswordHash())
                .authorities(user.getRole())
                .build();

        String token = jwtService.generateToken(principal, Map.of(
                "uid", user.getId(),
                "provider", user.getProvider().name()
        ));

        AuthResponse response = new AuthResponse();
        response.setToken(token);
        response.setExpiresInMs(jwtService.getExpirationMs());
        response.setUserId(user.getId());
        response.setEmail(user.getEmail());
        response.setName(user.getDisplayName());
        response.setProvider(user.getProvider().name());
        return response;
    }

    private UserProfileResponse toProfile(AppUser user) {
        UserProfileResponse profile = new UserProfileResponse();
        profile.setId(user.getId());
        profile.setEmail(user.getEmail());
        profile.setName(user.getDisplayName());
        profile.setProvider(user.getProvider().name());
        return profile;
    }

    private String readString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
