package com.cacanode.api.recruitment.api.event;

public record CandidatePrivacyDeletionConfirmationRequestedEvent(String email,String fullName,
        String companyName,String jobTitle,String locale,String confirmationUrl) {}
