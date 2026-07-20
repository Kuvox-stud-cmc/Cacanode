package com.cacanode.api.tenant;

public final class CustomerAnswerPromptDefaults {
    public static final String FALLBACK_TENANT_NAME = "this organization";
    public static final String LEGACY_PLATFORM_DEFAULT =
            "Answer from the tenant knowledge base when possible. "
                    + "Be clear when information is unavailable, avoid fabricating tenant-specific facts, "
                    + "and include citations when using retrieved sources.";
    public static final String PLATFORM_DEFAULT = forTenant(FALLBACK_TENANT_NAME);

    private CustomerAnswerPromptDefaults() {
    }

    public static String forTenant(String tenantName) {
        String displayName = normalizeTenantName(tenantName);
        return "You are the customer-facing assistant for " + displayName + ". "
                + "Always identify and represent the organization as " + displayName + ". "
                + "Respond to every customer message politely, helpfully, and in the requested locale. "
                + "Handle greetings, thanks, farewells, and light conversational messages naturally, "
                + "and offer relevant help without requiring a citation. "
                + "For questions about the products, services, policies, procedures, or other "
                + "organization-specific facts of " + displayName + ", answer only from supplied tenant "
                + "sources and cite the relevant sources. If the sources do not contain enough information, "
                + "say so politely and suggest a safe next step instead of guessing. Never fabricate "
                + "tenant-specific facts, claim an action was completed when it was not, or expose "
                + "information belonging to another tenant.";
    }

    public static boolean shouldUseTenantDefault(String prompt, String tenantName) {
        if (prompt == null || prompt.strip().isEmpty()) {
            return true;
        }
        String normalized = prompt.strip();
        return LEGACY_PLATFORM_DEFAULT.equals(normalized)
                || PLATFORM_DEFAULT.equals(normalized)
                || forTenant(tenantName).equals(normalized);
    }

    private static String normalizeTenantName(String tenantName) {
        if (tenantName == null || tenantName.strip().isEmpty()) {
            return FALLBACK_TENANT_NAME;
        }
        return tenantName.strip().replaceAll("\\s+", " ");
    }
}
