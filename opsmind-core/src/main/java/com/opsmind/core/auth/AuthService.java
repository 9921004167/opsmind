package com.opsmind.core.auth;

import com.opsmind.core.audit.AuditService;
import com.opsmind.core.auth.dto.*;
import com.opsmind.core.tenant.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final OrganizationRepository organizationRepository;
    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;

    public AuthResponse registerOrganization(RegisterOrganizationRequest request) {
        if (organizationRepository.existsBySlug(request.organizationSlug())) {
            throw new IllegalArgumentException("Organization slug already taken: " + request.organizationSlug());
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already registered: " + request.email());
        }

        Organization org = Organization.builder()
                .name(request.organizationName())
                .slug(request.organizationSlug())
                .build();
        org = organizationRepository.save(org);

        AppUser user = AppUser.builder()
                .organizationId(org.getId())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .role(Role.ADMIN)
                .enabled(true)
                .build();
        user = userRepository.save(user);

        auditService.record(org.getId(), user.getId(), "ORGANIZATION_REGISTERED", "Organization", org.getId().toString(), null);

        String token = jwtService.issueToken(user.getId(), org.getId(), user.getRole().name());
        return new AuthResponse(token, jwtService.expirationSeconds(), user.getId(), org.getId(), user.getRole().name());
    }

    public AuthResponse login(LoginRequest request) {
        AppUser user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        if (!user.isEnabled() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }
        auditService.record(user.getOrganizationId(), user.getId(), "USER_LOGIN", "AppUser", user.getId().toString(), null);
        String token = jwtService.issueToken(user.getId(), user.getOrganizationId(), user.getRole().name());
        return new AuthResponse(token, jwtService.expirationSeconds(), user.getId(), user.getOrganizationId(), user.getRole().name());
    }
}
