package com.cacanode.api.billing.api;

public class MessageQuotaExceededException extends RuntimeException {
    public MessageQuotaExceededException() {
        super("MESSAGE_QUOTA_EXCEEDED");
    }
}
