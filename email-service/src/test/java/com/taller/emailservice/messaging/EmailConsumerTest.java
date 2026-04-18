package com.taller.emailservice.messaging;

import com.taller.emailservice.service.EmailSenderService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EmailConsumerTest {

    @Test
    void shouldDelegateMessageToSenderService() {
        EmailSenderService senderService = mock(EmailSenderService.class);
        EmailConsumer consumer = new EmailConsumer(senderService);

        NotificationMessage message = message();

        consumer.consume(message, "message-12");

        verify(senderService).sendIfNotProcessed("message-12", message);
    }

    private NotificationMessage message() {
        return new NotificationMessage(
                12L,
                1L,
                "Ana Perez",
                "ana.perez@example.com",
                BigDecimal.valueOf(88.5),
            "PASSED",
                "2026-04-18T22:00:00"
        );
    }
}
