package com.taller.emailservice.messaging;

import com.taller.emailservice.service.EmailSenderService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class EmailConsumer {

    private final EmailSenderService emailSenderService;

    public EmailConsumer(EmailSenderService emailSenderService) {
        this.emailSenderService = emailSenderService;
    }

    @RabbitListener(queues = "${app.rabbit.queue}")
    public void consume(NotificationMessage message,
                        @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String brokerMessageId) {
        String messageId = resolveMessageId(brokerMessageId, message);
        emailSenderService.sendIfNotProcessed(messageId, message);
    }

    private String resolveMessageId(String brokerMessageId, NotificationMessage message) {
        if (brokerMessageId != null && !brokerMessageId.isBlank()) {
            return brokerMessageId;
        }
        return "fallback-" + message.examId() + "-" + message.evaluatedAt();
    }
}
