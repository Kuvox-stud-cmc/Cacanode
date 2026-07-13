package com.cacanode.api.tenant.dto;

import com.cacanode.api.tenant.enums.TicketPriority;
import com.cacanode.api.tenant.enums.TicketSource;
import com.cacanode.api.tenant.enums.TicketStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class TicketDtos {
    private TicketDtos() {
    }

    public record CreatePublicRequest(
            @NotNull UUID sessionId,
            @NotBlank @Email @Size(max = 320) String customerEmail,
            @Size(max = 255) String customerName,
            @NotBlank @Size(max = 255) String title,
            @NotBlank @Size(max = 10000) String description
    ) {
    }

    public record UpdateRequest(
            TicketStatus status,
            TicketPriority priority,
            UUID assignedTo,
            Boolean clearAssignee
    ) {
    }

    public record NoteRequest(@NotBlank @Size(max = 5000) String content) {
    }

    public record NoteResponse(
            UUID id,
            UUID authorId,
            String authorName,
            String content,
            LocalDateTime createdAt
    ) {
    }

    public record Response(
            UUID id,
            UUID chatbotId,
            UUID sessionId,
            String externalUserId,
            String customerName,
            String customerEmail,
            TicketSource source,
            String title,
            String description,
            TicketStatus status,
            TicketPriority priority,
            UUID assignedTo,
            String assignedToName,
            LocalDateTime resolvedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            List<NoteResponse> notes
    ) {
    }

    public record Assignee(UUID id, String fullName, String email) {
    }
}
