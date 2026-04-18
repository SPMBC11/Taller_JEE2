package com.taller.logica.service;

import com.taller.logica.dto.AnswerRequest;
import com.taller.logica.dto.DataAnswerDto;
import com.taller.logica.dto.FinishExamRequest;
import com.taller.logica.dto.FinishExamResponse;
import com.taller.logica.dto.PersistEvaluationRequest;
import com.taller.logica.dto.PersistEvaluationResponse;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;

@Stateless
public class ExamLogicService {

    private static final BigDecimal PASSING_SCORE = BigDecimal.valueOf(60);

    @EJB
    private DataServiceClient dataServiceClient;

    public FinishExamResponse finishExam(FinishExamRequest request) {
        validate(request);

        int totalQuestions = request.getAnswers().size();
        int correctAnswers = (int) request.getAnswers().stream().filter(AnswerRequest::isCorrect).count();
        BigDecimal score = calculateScore(totalQuestions, correctAnswers);
        String status = score.compareTo(PASSING_SCORE) >= 0 ? "APROBADO" : "REPROBADO";
        String now = OffsetDateTime.now().toString();

        PersistEvaluationRequest persistRequest = new PersistEvaluationRequest();
        persistRequest.setStudentId(request.getStudentId());
        persistRequest.setStartedAt(now);
        persistRequest.setFinishedAt(now);
        persistRequest.setTotalQuestions(totalQuestions);
        persistRequest.setCorrectAnswers(correctAnswers);
        persistRequest.setScore(score);
        persistRequest.setStatus(status);

        BigDecimal pointPerQuestion = totalQuestions == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(100).divide(BigDecimal.valueOf(totalQuestions), 2, RoundingMode.HALF_UP);

        for (AnswerRequest answerRequest : request.getAnswers()) {
            DataAnswerDto answerDto = new DataAnswerDto();
            answerDto.setQuestionId(answerRequest.getQuestionId());
            answerDto.setSelectedOption(answerRequest.getSelectedOption());
            answerDto.setCorrect(answerRequest.isCorrect());
            answerDto.setPoints(answerRequest.isCorrect() ? pointPerQuestion : BigDecimal.ZERO);
            persistRequest.getAnswers().add(answerDto);
        }

        PersistEvaluationResponse persisted = dataServiceClient.persistEvaluation(persistRequest);

        FinishExamResponse response = new FinishExamResponse();
        response.setExamId(persisted.getExamId());
        response.setScore(persisted.getScore());
        response.setStatus(persisted.getStatus());
        response.setEvaluatedAt(persisted.getEvaluatedAt());
        return response;
    }

    private void validate(FinishExamRequest request) {
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

    private BigDecimal calculateScore(int totalQuestions, int correctAnswers) {
        if (totalQuestions == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(correctAnswers)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalQuestions), 2, RoundingMode.HALF_UP);
    }
}
