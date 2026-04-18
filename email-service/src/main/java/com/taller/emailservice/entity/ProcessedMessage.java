package com.taller.emailservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "processed_messages")
public class ProcessedMessage {

    @Id
    @Column(name = "message_id", nullable = false, length = 120)
    private String messageId;

    @Column(name = "exam_id", nullable = false)
    private Long examId;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    protected ProcessedMessage() {
    }

    public ProcessedMessage(String messageId, Long examId, LocalDateTime processedAt) {
        this.messageId = messageId;
        this.examId = examId;
        this.processedAt = processedAt;
    }

    public String getMessageId() {
        return messageId;
    }

    public Long getExamId() {
        return examId;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }
}
