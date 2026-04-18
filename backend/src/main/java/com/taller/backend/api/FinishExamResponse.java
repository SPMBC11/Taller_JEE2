package com.taller.backend.api;

import java.math.BigDecimal;

public record FinishExamResponse(
        Long examId,
        BigDecimal score,
        String status
) {
}
