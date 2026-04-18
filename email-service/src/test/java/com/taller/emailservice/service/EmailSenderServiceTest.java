package com.taller.emailservice.service;

import com.taller.emailservice.messaging.NotificationMessage;
import com.taller.emailservice.repository.ProcessedMessageRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailSenderServiceTest {

    @Test
    void shouldNotSendRealEmailWhenMockModeIsEnabled() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        ProcessedMessageRepository processedMessageRepository = mock(ProcessedMessageRepository.class);
        EmailSenderService service = new EmailSenderService(
            mailSender,
            processedMessageRepository,
            "no-reply@taller.local",
            true
        );

        NotificationMessage message = new NotificationMessage(
                10L,
                1L,
                "Ana Perez",
                "ana.perez@example.com",
                BigDecimal.valueOf(70.25),
            "PASSED",
                "2026-04-18T20:00:00"
        );

        service.send(message);

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void shouldSendEmailWhenMockModeIsDisabled() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        ProcessedMessageRepository processedMessageRepository = mock(ProcessedMessageRepository.class);
        EmailSenderService service = new EmailSenderService(
            mailSender,
            processedMessageRepository,
            "no-reply@taller.local",
            false
        );

        NotificationMessage message = new NotificationMessage(
                11L,
                1L,
                "Ana Perez",
                "ana.perez@example.com",
                BigDecimal.valueOf(95.00),
            "PASSED",
                "2026-04-18T21:00:00"
        );

        service.send(message);

        ArgumentCaptor<SimpleMailMessage> mailCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(mailCaptor.capture());

        SimpleMailMessage sentMail = mailCaptor.getValue();
        assertEquals("no-reply@taller.local", sentMail.getFrom());
        assertEquals("ana.perez@example.com", sentMail.getTo()[0]);
        assertTrue(sentMail.getSubject().contains("#11"));
        assertTrue(sentMail.getText().contains("95.0"));
    }

    @Test
    void shouldSkipWhenMessageWasAlreadyProcessed() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        ProcessedMessageRepository processedMessageRepository = mock(ProcessedMessageRepository.class);
        when(processedMessageRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate message"));

        EmailSenderService service = new EmailSenderService(
                mailSender,
                processedMessageRepository,
                "no-reply@taller.local",
                false
        );

        NotificationMessage message = new NotificationMessage(
                99L,
                1L,
                "Ana Perez",
                "ana.perez@example.com",
                BigDecimal.valueOf(91.0),
                "PASSED",
                "2026-04-18T21:00:00"
        );

        boolean sent = service.sendIfNotProcessed("msg-99", message);

        assertFalse(sent);
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }
}
