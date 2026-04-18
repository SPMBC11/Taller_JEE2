package com.taller.emailservice.messaging;

import com.taller.emailservice.service.EmailSenderService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class EmailConsumer {

    private final EmailSenderService emailSenderService;

    public EmailConsumer(EmailSenderService emailSenderService) {
        this.emailSenderService = emailSenderService;
    }

    @RabbitListener(queues = "${app.rabbit.queue}")
    public void consume(NotificationMessage message) {
        emailSenderService.send(message);
    }
}
