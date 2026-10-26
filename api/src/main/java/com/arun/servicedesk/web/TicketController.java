package com.arun.servicedesk.web;

import com.arun.servicedesk.domain.TicketStatus;
import com.arun.servicedesk.dto.Dtos.*;
import com.arun.servicedesk.service.TicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {
    private final TicketService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TicketResponse create(Authentication auth, @Valid @RequestBody TicketCreateRequest r) {
        return service.create(auth.getName(), r);
    }

    @GetMapping
    public Page<TicketResponse> list(Authentication auth,
                                     @RequestParam(required = false) TicketStatus status,
                                     @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
                                     Pageable pageable) {
        return service.list(auth.getName(), status, pageable);
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    public Map<String, Long> stats() {
        return service.stats();
    }

    @GetMapping("/{id}")
    public TicketResponse get(Authentication auth, @PathVariable Long id) {
        return service.get(auth.getName(), id);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    public TicketResponse status(Authentication auth, @PathVariable Long id,
                                 @Valid @RequestBody StatusUpdateRequest r) {
        return service.changeStatus(auth.getName(), id, r.status());
    }

    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    public TicketResponse assign(Authentication auth, @PathVariable Long id,
                                 @Valid @RequestBody AssignRequest r) {
        return service.assign(auth.getName(), id, r.agentEmail());
    }

    @GetMapping("/{id}/comments")
    public List<CommentResponse> comments(Authentication auth, @PathVariable Long id) {
        return service.comments(auth.getName(), id);
    }

    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse addComment(Authentication auth, @PathVariable Long id,
                                      @Valid @RequestBody CommentRequest r) {
        return service.addComment(auth.getName(), id, r.body());
    }

    @GetMapping("/{id}/attachments")
    public List<AttachmentResponse> attachments(Authentication auth, @PathVariable Long id) {
        return service.attachments(auth.getName(), id);
    }

    @PostMapping(value = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public AttachmentResponse upload(Authentication auth, @PathVariable Long id,
                                     @RequestParam("file") MultipartFile file) {
        return service.addAttachment(auth.getName(), id, file);
    }
}
