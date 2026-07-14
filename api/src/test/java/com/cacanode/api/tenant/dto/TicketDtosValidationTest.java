package com.cacanode.api.tenant.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TicketDtosValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void noteRequiresTrimmedContentWithinFiveThousandCharacters() {
        assertTrue(validator.validate(new TicketDtos.NoteRequest(" ")).stream()
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("content")));
        assertTrue(validator.validate(new TicketDtos.NoteRequest("x".repeat(5001))).stream()
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("content")));
        assertEquals(0, validator.validate(new TicketDtos.NoteRequest("Internal note")).size());
    }
}
