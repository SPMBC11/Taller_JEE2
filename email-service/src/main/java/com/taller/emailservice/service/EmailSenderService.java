package com.taller.emailservice.service;

import com.taller.emailservice.messaging.NotificationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailSenderService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailSenderService.class);

    private final JavaMailSender mailSender;
    private final String from;
    private final boolean mockMode;

    public EmailSenderService(JavaMailSender mailSender,
                              @Value("${app.email.from}") String from,
                              @Value("${app.email.mock-mode:true}") boolean mockMode) {
        this.mailSender = mailSender;
        this.from = from;
        this.mockMode = mockMode;
    }

    public void send(NotificationMessage message) {
        String subject = "Resultado de evaluacion #" + message.examId();
        String body = "Hola " + message.studentName() + ",\n\n"
                + "Tu evaluacion ha finalizado.\n"
                + "Nota: " + message.score() + "\n"
                + "Estado: " + message.status() + "\n\n"
            + "Fecha: " + message.evaluatedAt() + "\n\n"
                + "Saludos,\nPlataforma Academica";

        if (mockMode) {
            LOGGER.info("[MOCK EMAIL] to={} subject={} body={} ", message.studentEmail(), subject, body);
            return;
        }

        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(from);
        mail.setTo(message.studentEmail());
        mail.setSubject(subject);
        mail.setText(body);
        mailSender.send(mail);
    }
}
