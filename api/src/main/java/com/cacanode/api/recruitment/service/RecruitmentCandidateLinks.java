package com.cacanode.api.recruitment.service;

import org.springframework.web.util.UriComponentsBuilder;

public final class RecruitmentCandidateLinks {
    private RecruitmentCandidateLinks() {}

    public static String withToken(String baseUrl,String parameter,String token) {
        return UriComponentsBuilder.fromUriString(baseUrl).queryParam(parameter,token).build().toUriString();
    }
}
