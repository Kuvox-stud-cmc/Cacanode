package com.cacanode.api.tenant;

public final class CustomerAnswerPromptDefaults {
    public static final String PLATFORM_DEFAULT = "Answer from the tenant knowledge base when possible. "
            + "Be clear when information is unavailable, avoid fabricating tenant-specific facts, "
            + "and include citations when using retrieved sources.";

    private CustomerAnswerPromptDefaults() {
    }
}
