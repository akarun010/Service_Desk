package com.arun.servicedesk.repo;

import com.arun.servicedesk.domain.Ticket;
import com.arun.servicedesk.domain.TicketStatus;
import com.arun.servicedesk.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    Page<Ticket> findByStatus(TicketStatus status, Pageable pageable);
    Page<Ticket> findByCreatedBy(User createdBy, Pageable pageable);
    Page<Ticket> findByCreatedByAndStatus(User createdBy, TicketStatus status, Pageable pageable);

    @Query("select t.status, count(t) from Ticket t group by t.status")
    List<Object[]> countByStatus();
}
