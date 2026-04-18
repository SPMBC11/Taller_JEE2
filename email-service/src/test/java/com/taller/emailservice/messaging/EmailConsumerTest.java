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

        NotificationMessage message = new NotificationMessage(
                12L,
                1L,
                "Ana Perez",
                "ana.perez@example.com",
                BigDecimal.valueOf(88.5),
                "APROBADO",
                "2026-04-18T22:00:00"
        );

        consumer.consume(message);

        verify(senderService).send(message);
    }
}
