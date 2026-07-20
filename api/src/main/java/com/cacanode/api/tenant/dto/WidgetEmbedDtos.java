package com.cacanode.api.tenant.dto;

import java.util.UUID;

public final class WidgetEmbedDtos {
    private WidgetEmbedDtos() {
    }

    public record Response(
            UUID tokenId,
            String tokenPrefix,
            boolean configured,
            String previewToken
    ) {
    }

    public record Generated(
            UUID tokenId,
            String tokenPrefix,
            String secret,
            String previewToken
    ) {
    }
}
