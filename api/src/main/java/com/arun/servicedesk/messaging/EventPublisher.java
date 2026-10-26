package com.arun.servicedesk.messaging;

import com.arun.servicedesk.config.RabbitConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventPublisher {
    private final RabbitTemplate rabbit;

    /** Best effort: a broker outage must not stop tickets from being created. */
    public void publish(String routingKey, TicketEvent event) {
        try {
            rabbit.convertAndSend(RabbitConfig.EXCHANGE, routingKey, event);
        } catch (AmqpException e) {
            log.error("Could not publish {} for ticket {}: {}", routingKey, event.ticketId(), e.getMessage());
        }
    }
}
