package com.cacanode.api.recruitment.api.event;

public record CandidateAccessEmailRequestedEvent(
        String email, String fullName, String companyName, String jobTitle,
        String locale, String accessUrl, boolean verification) {}
