package com.taller.backend.api;

import com.taller.backend.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/exams")
public class ExamController {

    private final TransactionService transactionService;

    public ExamController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/finish")
    public ResponseEntity<FinishExamResponse> finishExam(@Valid @RequestBody FinishExamRequest request) {
        FinishExamResponse response = transactionService.finalizeExam(request);
        return ResponseEntity.ok(response);
    }
}
