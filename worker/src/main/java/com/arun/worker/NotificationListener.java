package com.arun.worker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationListener {
    private final JavaMailSender mailSender;

    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void onEvent(TicketEvent e) {
        log.info("Received {} for ticket {}", e.eventType(), e.ticketId());

        // handy hook for testing the dead-letter queue: titles starting with [FAIL] always fail
        if (e.title() != null && e.title().startsWith("[FAIL]")) {
            throw new IllegalStateException("Simulated failure for ticket " + e.ticketId());
        }
        if (e.requesterEmail() == null) {
            log.warn("No recipient for ticket {}, skipping", e.ticketId());
            return;
        }

        String subject;
        String text;
        if ("TICKET_CREATED".equals(e.eventType())) {
            subject = "[Ticket #" + e.ticketId() + "] We received your request";
            text = "Hi " + e.requesterName() + ",\n\nYour ticket \"" + e.title()
                    + "\" was created and is now " + e.status() + ".\n\nServiceDesk";
        } else {
            subject = "[Ticket #" + e.ticketId() + "] Status updated to " + e.status();
            text = "Hi " + e.requesterName() + ",\n\nYour ticket \"" + e.title()
                    + "\" is now " + e.status() + ".\n\nServiceDesk";
        }

        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom("noreply@servicedesk.local");
        mail.setTo(e.requesterEmail());
        mail.setSubject(subject);
        mail.setText(text);
        mailSender.send(mail);
    }
}
