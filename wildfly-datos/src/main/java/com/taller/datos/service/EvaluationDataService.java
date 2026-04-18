package com.taller.datos.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taller.datos.dto.AnswerDto;
import com.taller.datos.dto.PersistEvaluationRequest;
import com.taller.datos.dto.PersistEvaluationResponse;
import com.taller.datos.entity.exam.Answer;
import com.taller.datos.entity.exam.Evaluation;
import com.taller.datos.entity.exam.ExamAttempt;
import com.taller.datos.entity.exam.OutboxEvent;
import com.taller.datos.entity.student.Grade;
import com.taller.datos.entity.student.ResultHistory;
import com.taller.datos.entity.student.Student;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

@Stateless
public class EvaluationDataService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @PersistenceContext(unitName = "examPU")
    private EntityManager examEntityManager;

    @PersistenceContext(unitName = "studentPU")
    private EntityManager studentEntityManager;

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public PersistEvaluationResponse persistEvaluation(PersistEvaluationRequest request) {
        validateRequest(request);

        Student student = studentEntityManager.find(Student.class, request.getStudentId());
        if (student == null) {
            throw new IllegalArgumentException("No existe el estudiante " + request.getStudentId());
        }

        LocalDateTime finishedAt = parseTimestampOrNow(request.getFinishedAt());
        LocalDateTime startedAt = request.getStartedAt() == null
                ? finishedAt
                : parseTimestampOrNow(request.getStartedAt());

        List<AnswerDto> answers = request.getAnswers();
        int totalQuestions = request.getTotalQuestions() == null ? answers.size() : request.getTotalQuestions();
        int correctAnswers = request.getCorrectAnswers() == null
                ? (int) answers.stream().filter(AnswerDto::isCorrect).count()
                : request.getCorrectAnswers();

        BigDecimal score = request.getScore() == null
                ? computeScore(totalQuestions, correctAnswers)
                : request.getScore().setScale(2, RoundingMode.HALF_UP);

        String status = request.getStatus() == null || request.getStatus().isBlank()
                ? defaultStatus(score)
                : request.getStatus();

        ExamAttempt examAttempt = new ExamAttempt();
        examAttempt.setStudentId(request.getStudentId());
        examAttempt.setStartedAt(startedAt);
        examAttempt.setFinishedAt(finishedAt);
        examAttempt.setTotalQuestions(totalQuestions);
        examAttempt.setCorrectAnswers(correctAnswers);
        examAttempt.setScore(score);

        BigDecimal pointPerQuestion = totalQuestions == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(100).divide(BigDecimal.valueOf(totalQuestions), 2, RoundingMode.HALF_UP);

        for (AnswerDto answerDto : answers) {
            Answer answer = new Answer();
            answer.setQuestionId(answerDto.getQuestionId());
            answer.setSelectedOption(answerDto.getSelectedOption());
            answer.setCorrect(answerDto.isCorrect());
            answer.setPoints(answerDto.getPoints() == null
                    ? (answerDto.isCorrect() ? pointPerQuestion : BigDecimal.ZERO)
                    : answerDto.getPoints());
            examAttempt.addAnswer(answer);
        }

        Evaluation evaluation = new Evaluation();
        evaluation.setScore(score);
        evaluation.setStatus(status);
        evaluation.setEvaluatedAt(finishedAt);
        examAttempt.addEvaluation(evaluation);

        examEntityManager.persist(examAttempt);
        examEntityManager.flush();

        Grade grade = new Grade();
        grade.setExamAttemptId(examAttempt.getId());
        grade.setStudent(student);
        grade.setScore(score);
        grade.setStatus(status);
        grade.setCreatedAt(finishedAt);
        studentEntityManager.persist(grade);

        ResultHistory history = new ResultHistory();
        history.setStudentId(student.getId());
        history.setExamAttemptId(examAttempt.getId());
        history.setScore(score);
        history.setStatus(status);
        history.setEvaluatedAt(finishedAt);
        studentEntityManager.persist(history);

        PersistEvaluationResponse response = new PersistEvaluationResponse();
        response.setExamId(examAttempt.getId());
        response.setStudentId(student.getId());
        response.setStudentName(student.getFullName());
        response.setStudentEmail(student.getEmail());
        response.setScore(score);
        response.setStatus(status);
        response.setEvaluatedAt(finishedAt.toString());

        OutboxEvent outboxEvent = buildOutboxEvent(response);
        examEntityManager.persist(outboxEvent);

        return response;
    }

    private OutboxEvent buildOutboxEvent(PersistEvaluationResponse response) {
        try {
            String payload = OBJECT_MAPPER.writeValueAsString(response);
            return OutboxEvent.pending(
                    "EVALUATION",
                    response.getExamId(),
                    "EVALUATION_FINISHED",
                    payload,
                    LocalDateTime.now()
            );
        } catch (JsonProcessingException exception) {
            throw new RuntimeException("No fue posible construir el evento de outbox", exception);
        }
    }

    private void validateRequest(PersistEvaluationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request invalido");
        }
        if (request.getStudentId() == null) {
            throw new IllegalArgumentException("studentId es obligatorio");
        }
        if (request.getAnswers() == null || request.getAnswers().isEmpty()) {
            throw new IllegalArgumentException("Debe existir al menos una respuesta");
        }
    }

    private LocalDateTime parseTimestampOrNow(String value) {
        if (value == null || value.isBlank()) {
            return LocalDateTime.now();
        }
        return OffsetDateTime.parse(value).toLocalDateTime();
    }

    private BigDecimal computeScore(int totalQuestions, int correctAnswers) {
        if (totalQuestions == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(correctAnswers)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalQuestions), 2, RoundingMode.HALF_UP);
    }

    private String defaultStatus(BigDecimal score) {
        return score.compareTo(BigDecimal.valueOf(60)) >= 0 ? "APROBADO" : "REPROBADO";
    }
}
