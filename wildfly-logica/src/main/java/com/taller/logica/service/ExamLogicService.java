package com.taller.logica.service;

import com.taller.logica.dto.AnswerRequest;
import com.taller.logica.dto.DataAnswerDto;
import com.taller.logica.dto.FinishExamRequest;
import com.taller.logica.dto.FinishExamResponse;
import com.taller.logica.dto.PersistEvaluationRequest;
import com.taller.logica.dto.PersistEvaluationResponse;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;

import java.time.OffsetDateTime;

@Stateless
public class ExamLogicService {

    @EJB
    private DataServiceClient dataServiceClient;

    public FinishExamResponse finishExam(FinishExamRequest request) {
        validate(request);

        String now = OffsetDateTime.now().toString();

        PersistEvaluationRequest persistRequest = new PersistEvaluationRequest();
        persistRequest.setStudentId(request.getStudentId());
        persistRequest.setStartedAt(now);
        persistRequest.setFinishedAt(now);

        for (AnswerRequest answerRequest : request.getAnswers()) {
            DataAnswerDto answerDto = new DataAnswerDto();
            answerDto.setQuestionId(answerRequest.getQuestionId());
            answerDto.setSelectedOption(answerRequest.getSelectedOption());
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
        for (AnswerRequest answer : request.getAnswers()) {
            if (answer.getQuestionId() == null) {
                throw new IllegalArgumentException("questionId es obligatorio en cada respuesta");
            }
            if (answer.getSelectedOption() == null || answer.getSelectedOption().isBlank()) {
                throw new IllegalArgumentException("selectedOption es obligatorio en cada respuesta");
            }
        }
    }
}
