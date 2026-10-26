package com.arun.servicedesk.repo;

import com.arun.servicedesk.domain.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
    List<Attachment> findByTicketIdOrderByUploadedAtAsc(Long ticketId);
}
