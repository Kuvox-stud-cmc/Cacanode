package com.cacanode.api.ai.api;

public class InterviewInferenceException extends RuntimeException {
    private final String code;

    public InterviewInferenceException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
