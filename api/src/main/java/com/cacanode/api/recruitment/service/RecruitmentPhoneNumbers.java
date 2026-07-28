package com.cacanode.api.recruitment.service;

import com.cacanode.api.common.exception.custom.BadRequestException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class RecruitmentPhoneNumbers {
    static final Set<String> SUPPORTED_REGIONS = Set.of(
            "AU", "CA", "GB", "ID", "IN", "JP", "KR", "MY", "PH", "SG", "TH", "US", "VN");

    public String normalizeRequired(String raw) {
        if (raw == null || raw.isBlank()) throw invalid();
        try {
            PhoneNumberUtil util = PhoneNumberUtil.getInstance();
            PhoneNumber number = util.parse(raw.strip(), null);
            String region = util.getRegionCodeForNumber(number);
            if (region == null || !SUPPORTED_REGIONS.contains(region) || !util.isValidNumberForRegion(number, region))
                throw invalid();
            return util.format(number, PhoneNumberUtil.PhoneNumberFormat.E164);
        } catch (BadRequestException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid();
        }
    }

    public String normalizeOptional(String raw) {
        return raw == null || raw.isBlank() ? null : normalizeRequired(raw);
    }

    private static BadRequestException invalid() {
        return new BadRequestException("Phone number must be valid for a supported country or region");
    }
}
