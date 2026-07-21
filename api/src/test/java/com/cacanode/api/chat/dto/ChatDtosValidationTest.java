package com.cacanode.api.chat.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatDtosValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void widgetMessageAcceptsOnlySupportedOptionalLocales() {
        assertEquals(0, validator.validate(
                new ChatDtos.WidgetSubmitMessageRequest("Hello", Map.of(), null)).size());
        assertEquals(0, validator.validate(
                new ChatDtos.WidgetSubmitMessageRequest("Hello", Map.of(), "en-US")).size());
        assertEquals(0, validator.validate(
                new ChatDtos.WidgetSubmitMessageRequest("Xin chào", Map.of(), "vi-VN")).size());

        assertTrue(validator.validate(
                        new ChatDtos.WidgetSubmitMessageRequest("Hello", Map.of(), "en-GB"))
                .stream()
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("locale")));
    }
}
