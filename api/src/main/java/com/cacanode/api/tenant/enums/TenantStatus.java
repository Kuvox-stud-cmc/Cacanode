package com.cacanode.api.tenant.enums;

public enum TenantStatus {
    /**
     * paying, everything works normally
     * */
    ACTIVE,

    /**
     * account exists but chatbot is disabled
     * (admin deactivated it, or tenant paused subscription)
     * */
    INACTIVE,

    /**
     * violated terms or failed payment
     * chatbot stopped, admin dashboard still accessible
     * to resolve the issue
     * */
    SUSPENDED,

    /**
     * new tenant in free trial period
     * limited usage, no payment yet
     * */
    TRIAL,

    /**
     * trial ended and did not subscribe
     * or subscription lapsed
     * dashboard accessible but chatbot disabled
     * */
    EXPIRED,

    /**
     * just registered, email not verified yet
     * */
    PENDING
}
