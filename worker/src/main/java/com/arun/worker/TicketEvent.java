package com.arun.worker;

public record TicketEvent(Long ticketId, String title, String status, String eventType,
                          String requesterEmail, String requesterName, String occurredAt) {}
