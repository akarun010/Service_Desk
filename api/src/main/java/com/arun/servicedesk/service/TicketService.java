package com.arun.servicedesk.service;

import com.arun.servicedesk.domain.*;
import com.arun.servicedesk.dto.Dtos.*;
import com.arun.servicedesk.messaging.EventPublisher;
import com.arun.servicedesk.messaging.TicketEvent;
import com.arun.servicedesk.repo.*;
import com.arun.servicedesk.web.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TicketService {
    private final TicketRepository tickets;
    private final UserRepository users;
    private final CommentRepository comments;
    private final AttachmentRepository attachments;
    private final StorageService storage;
    private final EventPublisher events;

    public record Download(String fileName, String contentType, InputStream stream) {}

    // ---------- tickets ----------

    @Transactional
    @CacheEvict(value = "ticket-stats", allEntries = true)
    public TicketResponse create(String email, TicketCreateRequest r) {
        User u = currentUser(email);
        Ticket t = new Ticket();
        t.setTitle(r.title());
        t.setDescription(r.description());
        t.setPriority(r.priority() == null ? Priority.MEDIUM : r.priority());
        t.setStatus(TicketStatus.OPEN);
        t.setCreatedBy(u);
        t = tickets.save(t);
        events.publish("ticket.created", event(t, "TICKET_CREATED"));
        return ticketDto(t);
    }

    @Transactional(readOnly = true)
    public Page<TicketResponse> list(String email, TicketStatus status, Pageable pageable) {
        User u = currentUser(email);
        Page<Ticket> page;
        if (isStaff(u)) {
            page = status == null ? tickets.findAll(pageable) : tickets.findByStatus(status, pageable);
        } else {
            page = status == null ? tickets.findByCreatedBy(u, pageable)
                                  : tickets.findByCreatedByAndStatus(u, status, pageable);
        }
        return page.map(this::ticketDto);
    }

    @Transactional(readOnly = true)
    public TicketResponse get(String email, Long id) {
        return ticketDto(load(currentUser(email), id));
    }

    @Transactional
    @CacheEvict(value = "ticket-stats", allEntries = true)
    public TicketResponse changeStatus(String email, Long id, TicketStatus status) {
        Ticket t = load(currentUser(email), id);
        t.setStatus(status);
        events.publish("ticket.status_changed", event(t, "STATUS_CHANGED"));
        return ticketDto(t);
    }

    @Transactional
    public TicketResponse assign(String email, Long id, String agentEmail) {
        Ticket t = load(currentUser(email), id);
        User agent = users.findByEmail(agentEmail.trim().toLowerCase())
                .filter(this::isStaff)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "No agent with that email"));
        t.setAssignee(agent);
        return ticketDto(t);
    }

    @Cacheable("ticket-stats")
    @Transactional(readOnly = true)
    public Map<String, Long> stats() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (TicketStatus s : TicketStatus.values()) result.put(s.name(), 0L);
        for (Object[] row : tickets.countByStatus()) {
            result.put(((TicketStatus) row[0]).name(), (Long) row[1]);
        }
        return result;
    }

    // ---------- comments ----------

    @Transactional
    public CommentResponse addComment(String email, Long ticketId, String body) {
        User u = currentUser(email);
        Ticket t = load(u, ticketId);
        Comment c = new Comment();
        c.setTicket(t);
        c.setAuthor(u);
        c.setBody(body);
        return commentDto(comments.save(c));
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> comments(String email, Long ticketId) {
        load(currentUser(email), ticketId);
        return comments.findByTicketIdOrderByCreatedAtAsc(ticketId).stream().map(this::commentDto).toList();
    }

    // ---------- attachments ----------

    @Transactional
    public AttachmentResponse addAttachment(String email, Long ticketId, MultipartFile file) {
        if (file.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "File is empty");
        Ticket t = load(currentUser(email), ticketId);
        String name = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String type = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        String key = storage.upload(file, name, type);

        Attachment a = new Attachment();
        a.setTicket(t);
        a.setObjectKey(key);
        a.setFileName(name);
        a.setContentType(type);
        a.setSizeBytes(file.getSize());
        return attachmentDto(attachments.save(a));
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> attachments(String email, Long ticketId) {
        load(currentUser(email), ticketId);
        return attachments.findByTicketIdOrderByUploadedAtAsc(ticketId).stream().map(this::attachmentDto).toList();
    }

    @Transactional(readOnly = true)
    public Download download(String email, Long attachmentId) {
        User u = currentUser(email);
        Attachment a = attachments.findById(attachmentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Attachment not found"));
        load(u, a.getTicket().getId());
        return new Download(a.getFileName(), a.getContentType(), storage.download(a.getObjectKey()));
    }

    // ---------- helpers ----------

    private User currentUser(String email) {
        return users.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unknown user"));
    }

    private boolean isStaff(User u) {
        return u.getRole() != Role.USER;
    }

    private Ticket load(User u, Long id) {
        Ticket t = tickets.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Ticket not found"));
        if (!isStaff(u) && !t.getCreatedBy().getId().equals(u.getId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Ticket not found");
        }
        return t;
    }

    private TicketEvent event(Ticket t, String type) {
        User c = t.getCreatedBy();
        return new TicketEvent(t.getId(), t.getTitle(), t.getStatus().name(), type,
                c.getEmail(), c.getFullName(), Instant.now().toString());
    }

    private TicketResponse ticketDto(Ticket t) {
        return new TicketResponse(t.getId(), t.getTitle(), t.getDescription(), t.getStatus(), t.getPriority(),
                t.getCreatedBy().getEmail(),
                t.getAssignee() == null ? null : t.getAssignee().getEmail(),
                t.getCreatedAt(), t.getUpdatedAt());
    }

    private CommentResponse commentDto(Comment c) {
        return new CommentResponse(c.getId(), c.getAuthor().getEmail(), c.getBody(), c.getCreatedAt());
    }

    private AttachmentResponse attachmentDto(Attachment a) {
        return new AttachmentResponse(a.getId(), a.getFileName(), a.getContentType(), a.getSizeBytes());
    }
}
