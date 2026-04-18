package com.taller.backend.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class QueuePublisher {

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String routingKey;

    public QueuePublisher(RabbitTemplate rabbitTemplate,
                          @Value("${app.rabbit.exchange}") String exchange,
                          @Value("${app.rabbit.routing-key}") String routingKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.routingKey = routingKey;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishAfterCommit(ExamFinishedEvent event) {
        NotificationMessage message = new NotificationMessage(
                event.examId(),
                event.studentId(),
                event.studentName(),
                event.studentEmail(),
                event.score(),
                event.status()
        );
        rabbitTemplate.convertAndSend(exchange, routingKey, message);
    }
}
