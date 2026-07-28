package com.cacanode.api.recruitment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix="app.recruitment.cost")
public record RecruitmentCostProperties(String rateCardVersion,BigDecimal twilioCallUsdPerMinute,
        BigDecimal recordingUsdPerMinute,BigDecimal cartesiaUsdPerMillionCharacters,
        BigDecimal modelUsdPerMillionTokens) {}
