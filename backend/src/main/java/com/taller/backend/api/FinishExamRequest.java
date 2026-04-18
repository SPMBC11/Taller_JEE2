package com.taller.backend.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record FinishExamRequest(
        @NotNull Long studentId,
        @NotEmpty List<@Valid AnswerRequest> answers
) {
}
