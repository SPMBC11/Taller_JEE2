package com.taller.backend.messaging;

import java.math.BigDecimal;

public record NotificationMessage(
        Long examId,
        Long studentId,
        String studentName,
        String studentEmail,
        BigDecimal score,
        String status
) {
}
