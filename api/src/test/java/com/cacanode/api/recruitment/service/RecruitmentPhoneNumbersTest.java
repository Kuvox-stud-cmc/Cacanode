package com.cacanode.api.recruitment.service;

import com.cacanode.api.common.exception.custom.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecruitmentPhoneNumbersTest {
    private final RecruitmentPhoneNumbers phoneNumbers = new RecruitmentPhoneNumbers();

    @Test void normalizesSupportedInternationalNumbers() {
        assertEquals("+84901234567",phoneNumbers.normalizeRequired(" +84901234567 "));
        assertEquals("+14155552671",phoneNumbers.normalizeRequired("+14155552671"));
        assertEquals("+14165551234",phoneNumbers.normalizeRequired("+14165551234"));
        assertEquals("+6591234567",phoneNumbers.normalizeRequired("+6591234567"));
        assertEquals("+447400123456",phoneNumbers.normalizeRequired("+447400123456"));
    }

    @Test void rejectsInvalidAndUnsupportedNumbers() {
        assertThrows(BadRequestException.class,()->phoneNumbers.normalizeRequired("+84123"));
        assertThrows(BadRequestException.class,()->phoneNumbers.normalizeRequired("+33612345678"));
        assertThrows(BadRequestException.class,()->phoneNumbers.normalizeRequired("0901234567"));
    }

    @Test void treatsBlankOptionalNumbersAsAbsent() {
        assertNull(phoneNumbers.normalizeOptional(null));
        assertNull(phoneNumbers.normalizeOptional("  "));
    }
}
