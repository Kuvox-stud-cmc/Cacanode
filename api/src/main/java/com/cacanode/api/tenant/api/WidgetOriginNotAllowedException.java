package com.cacanode.api.tenant.api;

import org.springframework.security.access.AccessDeniedException;

/**
 * A widget credential was presented from a website origin that the chatbot does not allow.
 *
 * <p>Extends {@link AccessDeniedException} so existing security handling keeps treating this as a
 * forbidden request, while allowing the public widget surface to report the actionable reason.
 */
public class WidgetOriginNotAllowedException extends AccessDeniedException {
    public WidgetOriginNotAllowedException() {
        super("Website origin is not allowed");
    }
}
