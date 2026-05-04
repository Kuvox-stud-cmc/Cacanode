package com.cacanode.api.tenant.service.implement;

import com.cacanode.api.common.event.UserRegisteredEvent;
import com.cacanode.api.tenant.api.TenantModuleApi;
import com.cacanode.api.tenant.dto.RegisterTenantCommand;
import com.cacanode.api.tenant.dto.TenantUserResult;
import com.cacanode.api.tenant.dto.UserAuthDto;
import com.cacanode.api.tenant.enums.TenantPlan;
import com.cacanode.api.tenant.enums.TenantStatus;
import com.cacanode.api.tenant.enums.UserRole;
import com.cacanode.api.tenant.enums.UserStatus;
import com.cacanode.api.tenant.model.Tenant;
import com.cacanode.api.tenant.model.User;
import com.cacanode.api.tenant.repository.TenantRepository;
import com.cacanode.api.tenant.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@Slf4j(topic = "TENANT-API")
@RequiredArgsConstructor
public class TenantModuleApiImpl implements TenantModuleApi {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public TenantUserResult registerTenantWithAdmin(RegisterTenantCommand command) {

        // 1. Create tenant
        Tenant tenant = new Tenant();
        tenant.setName(command.getCompanyName());
        tenant.setSlug(generateSlug(command.getCompanyName()));
        tenant.setPlan(TenantPlan.TRIAL);
        tenant.setStatus(TenantStatus.TRIAL);
        tenant.setTrialEndsAt(LocalDateTime.now().plusDays(14));
        tenant.setLlmProvider("groq");
        tenant.setLlmModel("llama-3.3-70b-versatile");
        tenant.setEmbedProvider("voyageai");
        tenant.setEmbedModel("voyage-3");
        tenant.setMaxDocuments(30);
        tenant.setMaxMessages(1000);
        tenant.setMaxStorageMb(1024);
        tenantRepository.save(tenant);

        // 2. Create admin user - tenant module owns users table
        User user = new User();
        user.setTenant(tenant);
        user.setEmail(command.getEmail());
        user.setPasswordHash(command.getPasswordHash());
        user.setFullName(command.getFullName());
        user.setRole(UserRole.TENANT_ADMIN);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        log.info("Tenant and admin user created: tenantId={}, userId={}", tenant.getId(), user.getId());

        // 3. Publish event
        eventPublisher.publishEvent(
                new UserRegisteredEvent(this, user.getId(), tenant.getId(), user.getEmail())
        );

        // 4. Return result - no entity crosses the boundary
        return TenantUserResult.builder()
                .tenantId(tenant.getId())
                .userId(user.getId())
                .email(user.getEmail())
                .role(user.getRole().name())
                .plan(tenant.getPlan().name())
                .status(tenant.getStatus().name())
                .build();
    }

    @Override
    public UserAuthDto findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(user -> UserAuthDto.builder()
                        .userId(user.getId())
                        .tenantId(user.getTenant().getId())
                        .email(user.getEmail())
                        .passwordHash(user.getPasswordHash())
                        .role(user.getRole().name())
                        .status(user.getStatus().name())
                        .tenantStatus(user.getTenant().getStatus().name())
                        .build()
                )
                .orElse(null);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    private String generateSlug(String companyName) {
        String normalized = Normalizer.normalize(companyName, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCOMBINING_DIACRITICAL_MARKS}+");
        String slug = pattern.matcher(normalized)
                .replaceAll("")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("[\\s]+", "-")
                .trim();

        // Ensure uniqueness by appending random suffix if slug exists
        if (tenantRepository.existsBySlug(slug)) {
            slug = slug + "-" + java.util.UUID.randomUUID().toString().substring(0, 6);
        }

        return slug;
    }
}
