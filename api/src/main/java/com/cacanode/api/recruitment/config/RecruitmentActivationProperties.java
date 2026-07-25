package com.cacanode.api.recruitment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix="app.recruitment.activation")
public record RecruitmentActivationProperties(boolean gaUnlocked) {}
