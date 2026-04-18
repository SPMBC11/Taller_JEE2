package com.taller.datos.messaging;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.taller.datos.entity.exam.OutboxEvent;
import jakarta.annotation.PostConstruct;
import jakarta.ejb.Schedule;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeoutException;

@Singleton
@Startup
public class OutboxPublisherService {

    private static final int BATCH_SIZE = 25;
    private static final int MAX_ERROR_LENGTH = 1000;

    @PersistenceContext(unitName = "examPU")
    private EntityManager examEntityManager;

    private String exchange;
    private String queue;
    private String routingKey;

    @PostConstruct
    void init() {
        this.exchange = resolveEnv("QUEUE_EXCHANGE", "exam.exchange");
        this.queue = resolveEnv("QUEUE_NAME", "exam.notifications");
        this.routingKey = resolveEnv("QUEUE_ROUTING_KEY", "exam.finished");
    }

    @Schedule(hour = "*", minute = "*", second = "*/5", persistent = false)
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void dispatchPendingEvents() {
        LocalDateTime now = LocalDateTime.now();
        List<OutboxEvent> events = examEntityManager.createQuery(
                        "SELECT e FROM OutboxEvent e " +
                                "WHERE (e.status = :pending OR e.status = :failed) " +
                                "AND e.nextAttemptAt <= :now ORDER BY e.createdAt",
                        OutboxEvent.class)
                .setParameter("pending", OutboxEvent.STATUS_PENDING)
                .setParameter("failed", OutboxEvent.STATUS_FAILED)
                .setParameter("now", now)
                .setMaxResults(BATCH_SIZE)
                .getResultList();

        if (events.isEmpty()) {
            return;
        }

        ConnectionFactory connectionFactory = buildConnectionFactory();

        try (Connection connection = connectionFactory.newConnection("wildfly-datos-outbox-publisher");
             Channel channel = connection.createChannel()) {
            channel.exchangeDeclare(exchange, BuiltinExchangeType.TOPIC, true);
            channel.queueDeclare(queue, true, false, false, null);
            channel.queueBind(queue, exchange, routingKey);

            for (OutboxEvent event : events) {
                publishEvent(channel, event);
                examEntityManager.merge(event);
            }
        } catch (IOException | TimeoutException exception) {
            for (OutboxEvent event : events) {
                registerFailure(event, "RabbitMQ no disponible: " + exception.getMessage(), now);
                examEntityManager.merge(event);
            }
        }
    }

    private void publishEvent(Channel channel, OutboxEvent event) {
        try {
            AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                    .contentType("application/json")
                    .deliveryMode(2)
                    .messageId(String.valueOf(event.getId()))
                    .timestamp(new Date())
                    .build();

            channel.basicPublish(
                    exchange,
                    routingKey,
                    properties,
                    event.getPayload().getBytes(StandardCharsets.UTF_8)
            );
            event.markSent(LocalDateTime.now());
        } catch (IOException exception) {
            registerFailure(event, exception.getMessage(), LocalDateTime.now());
        }
    }

    private void registerFailure(OutboxEvent event, String message, LocalDateTime now) {
        int attemptCount = event.getAttemptCount() == null ? 0 : event.getAttemptCount();
        long nextDelaySeconds = Math.min(300L, 5L * (1L << Math.min(attemptCount, 6)));
        String safeMessage = message == null ? "Error desconocido" : message;
        if (safeMessage.length() > MAX_ERROR_LENGTH) {
            safeMessage = safeMessage.substring(0, MAX_ERROR_LENGTH);
        }
        event.markFailed(safeMessage, now.plusSeconds(nextDelaySeconds));
    }

    private ConnectionFactory buildConnectionFactory() {
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost(resolveEnv("QUEUE_HOST", "queue"));
        connectionFactory.setPort(Integer.parseInt(resolveEnv("QUEUE_PORT", "5672")));
        connectionFactory.setUsername(resolveEnv("QUEUE_USER", "guest"));
        connectionFactory.setPassword(resolveEnv("QUEUE_PASSWORD", "guest"));
        return connectionFactory;
    }

    private String resolveEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }
}
