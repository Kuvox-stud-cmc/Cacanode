package com.cacanode.api.auth.exception;

public class MobileRoleUnsupportedException extends RuntimeException {
    public static final String CODE = "MOBILE_ROLE_UNSUPPORTED";

    public MobileRoleUnsupportedException() {
        super(CODE);
    }
}
