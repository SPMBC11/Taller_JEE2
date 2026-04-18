package com.taller.backend.service;

import com.taller.backend.api.AnswerRequest;
import com.taller.backend.api.FinishExamRequest;
import com.taller.backend.api.FinishExamResponse;
import com.taller.backend.bd1.entity.Answer;
import com.taller.backend.bd1.entity.ExamAttempt;
import com.taller.backend.bd1.repository.ExamRepository;
import com.taller.backend.bd2.entity.Grade;
import com.taller.backend.bd2.entity.Student;
import com.taller.backend.bd2.repository.GradeRepository;
import com.taller.backend.bd2.repository.StudentRepository;
import com.taller.backend.messaging.ExamFinishedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
public class TransactionService {

    private static final BigDecimal PASSING_SCORE = BigDecimal.valueOf(60);

    private final ExamRepository examRepository;
    private final StudentRepository studentRepository;
    private final GradeRepository gradeRepository;
    private final ApplicationEventPublisher eventPublisher;

    public TransactionService(ExamRepository examRepository,
                              StudentRepository studentRepository,
                              GradeRepository gradeRepository,
                              ApplicationEventPublisher eventPublisher) {
        this.examRepository = examRepository;
        this.studentRepository = studentRepository;
        this.gradeRepository = gradeRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public FinishExamResponse finalizeExam(FinishExamRequest request) {
        Student student = studentRepository.findById(request.studentId())
                .orElseThrow(() -> new IllegalArgumentException("No existe el estudiante " + request.studentId()));

        int total = request.answers().size();
        long correct = request.answers().stream().filter(AnswerRequest::correct).count();
        BigDecimal score = calculateScore(total, correct);
        String status = score.compareTo(PASSING_SCORE) >= 0 ? "APROBADO" : "REPROBADO";

        ExamAttempt attempt = new ExamAttempt();
        attempt.setStudentId(student.getId());
        attempt.setStartedAt(LocalDateTime.now());
        attempt.setFinishedAt(LocalDateTime.now());
        attempt.setTotalQuestions(total);
        attempt.setCorrectAnswers((int) correct);
        attempt.setScore(score);

        BigDecimal pointsPerQuestion = total == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(100).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);

        for (AnswerRequest answerRequest : request.answers()) {
            Answer answer = new Answer();
            answer.setQuestionId(answerRequest.questionId());
            answer.setSelectedOption(answerRequest.selectedOption());
            answer.setCorrect(answerRequest.correct());
            answer.setPoints(answerRequest.correct() ? pointsPerQuestion : BigDecimal.ZERO);
            attempt.addAnswer(answer);
        }

        ExamAttempt savedAttempt = examRepository.save(attempt);

        Grade grade = new Grade();
        grade.setExamAttemptId(savedAttempt.getId());
        grade.setStudent(student);
        grade.setScore(score);
        grade.setStatus(status);
        grade.setCreatedAt(LocalDateTime.now());
        gradeRepository.save(grade);

        eventPublisher.publishEvent(new ExamFinishedEvent(
                savedAttempt.getId(),
                student.getId(),
                student.getFullName(),
                student.getEmail(),
                score,
                status
        ));

        return new FinishExamResponse(savedAttempt.getId(), score, status);
    }

    private BigDecimal calculateScore(int total, long correct) {
        if (total == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(correct)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }
}
