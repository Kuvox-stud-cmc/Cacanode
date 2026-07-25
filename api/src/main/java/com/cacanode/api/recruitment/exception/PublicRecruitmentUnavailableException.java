package com.cacanode.api.recruitment.exception;

public class PublicRecruitmentUnavailableException extends RuntimeException {
    public PublicRecruitmentUnavailableException(String message) { super(message); }
    public PublicRecruitmentUnavailableException(String message, Throwable cause) { super(message, cause); }
}
