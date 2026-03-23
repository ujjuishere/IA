package com.example.demo.repository;

import com.example.demo.entity.AppUser;
import com.example.demo.entity.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);

    Optional<AppUser> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);

    boolean existsByEmail(String email);
}
