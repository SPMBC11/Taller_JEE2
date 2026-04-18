package com.taller.backend.service;

import com.taller.backend.api.AnswerRequest;
import com.taller.backend.api.FinishExamRequest;
import com.taller.backend.api.FinishExamResponse;
import com.taller.backend.bd1.entity.ExamAttempt;
import com.taller.backend.bd1.repository.ExamRepository;
import com.taller.backend.bd2.entity.Grade;
import com.taller.backend.bd2.entity.Student;
import com.taller.backend.bd2.repository.GradeRepository;
import com.taller.backend.bd2.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.mockito.Mockito.mock;

class TransactionServiceTest {

    private ExamRepository examRepository;
    private StudentRepository studentRepository;
    private GradeRepository gradeRepository;
    private ApplicationEventPublisher eventPublisher;

    private TransactionService transactionService;

    @BeforeEach
    void setUp() {
        examRepository = mock(ExamRepository.class);
        studentRepository = mock(StudentRepository.class);
        gradeRepository = mock(GradeRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        openMocks(this);

        transactionService = new TransactionService(examRepository, studentRepository, gradeRepository, eventPublisher);
    }

    @Test
    void shouldSaveExamGradeAndPublishEvent() {
        Student student = new Student("Ana Perez", "ana.perez@example.com");

        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(examRepository.save(any(ExamAttempt.class))).thenAnswer(invocation -> {
            ExamAttempt attempt = invocation.getArgument(0);
            try {
                var idField = ExamAttempt.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(attempt, 10L);
            } catch (ReflectiveOperationException exception) {
                throw new RuntimeException(exception);
            }
            return attempt;
        });
        when(gradeRepository.save(any(Grade.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FinishExamRequest request = new FinishExamRequest(
                1L,
                List.of(
                        new AnswerRequest(1L, "A", true),
                        new AnswerRequest(2L, "B", false),
                        new AnswerRequest(3L, "C", true)
                )
        );

        FinishExamResponse response = transactionService.finalizeExam(request);

        assertEquals(10L, response.examId());
        assertEquals("66.67", response.score().toString());
        assertEquals("APROBADO", response.status());

        verify(examRepository, times(1)).save(any(ExamAttempt.class));
        verify(gradeRepository, times(1)).save(any(Grade.class));
        verify(eventPublisher, times(1)).publishEvent(any(Object.class));
    }

    @Test
    void shouldFailWhenStudentNotFound() {
        when(studentRepository.findById(99L)).thenReturn(Optional.empty());

        FinishExamRequest request = new FinishExamRequest(
                99L,
                List.of(new AnswerRequest(1L, "A", true))
        );

        assertThrows(IllegalArgumentException.class, () -> transactionService.finalizeExam(request));

        verify(examRepository, never()).save(any());
        verify(gradeRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void shouldNotPublishEventWhenSavingGradeFails() {
        Student student = new Student("Ana Perez", "ana.perez@example.com");

        when(studentRepository.findById(1L)).thenReturn(Optional.of(student));
        when(examRepository.save(any(ExamAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(gradeRepository.save(any(Grade.class))).thenThrow(new RuntimeException("BD2 failure"));

        FinishExamRequest request = new FinishExamRequest(
                1L,
                List.of(new AnswerRequest(1L, "A", true))
        );

        assertThrows(RuntimeException.class, () -> transactionService.finalizeExam(request));

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }
}
