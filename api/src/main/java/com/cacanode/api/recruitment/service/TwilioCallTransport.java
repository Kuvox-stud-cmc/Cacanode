package com.cacanode.api.recruitment.service;

public interface TwilioCallTransport {
    CreatedCall create(CreateCall command);
    boolean terminate(String callSid);

    record CreateCall(String destination,String voiceUrl,String fallbackUrl,String statusUrl,
                      int durationLimitSeconds) {}
    record CreatedCall(String callSid) {}

    final class DefiniteFailure extends RuntimeException {
        public DefiniteFailure(String message,Throwable cause){super(message,cause);}
    }
    final class UncertainFailure extends RuntimeException {
        public UncertainFailure(String message,Throwable cause){super(message,cause);}
    }
}
