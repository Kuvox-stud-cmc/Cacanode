package com.cacanode.api.recruitment.exception;

public class PublicRecruitmentRateLimitException extends RuntimeException {
    private final long retryAfterSeconds;
    public PublicRecruitmentRateLimitException(long retryAfterSeconds) {
        super("Too many application attempts");
        this.retryAfterSeconds = retryAfterSeconds;
    }
    public long retryAfterSeconds() { return retryAfterSeconds; }
}
