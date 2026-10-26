package com.arun.servicedesk.dto;

import com.arun.servicedesk.domain.Priority;
import com.arun.servicedesk.domain.TicketStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class Dtos {
    private Dtos() {}

    public record RegisterRequest(@NotBlank @Email String email,
                                  @NotBlank @Size(min = 8, max = 72) String password,
                                  @NotBlank String fullName) {}

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}

    public record AuthResponse(String token, String email, String fullName, String role) {}

    public record TicketCreateRequest(@NotBlank @Size(max = 200) String title,
                                      @NotBlank String description,
                                      Priority priority) {}

    public record StatusUpdateRequest(@NotNull TicketStatus status) {}

    public record AssignRequest(@NotBlank @Email String agentEmail) {}

    public record CommentRequest(@NotBlank String body) {}

    public record TicketResponse(Long id, String title, String description, TicketStatus status,
                                 Priority priority, String createdBy, String assignee,
                                 Instant createdAt, Instant updatedAt) {}

    public record CommentResponse(Long id, String author, String body, Instant createdAt) {}

    public record AttachmentResponse(Long id, String fileName, String contentType, long sizeBytes) {}
}
