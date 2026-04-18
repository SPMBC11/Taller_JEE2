package com.taller.backend.service;

import com.taller.backend.bd1.entity.Question;
import com.taller.backend.bd1.repository.QuestionRepository;
import com.taller.backend.bd2.entity.Student;
import com.taller.backend.bd2.repository.StudentRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements CommandLineRunner {

    private final QuestionRepository questionRepository;
    private final StudentRepository studentRepository;

    public DataSeeder(QuestionRepository questionRepository, StudentRepository studentRepository) {
        this.questionRepository = questionRepository;
        this.studentRepository = studentRepository;
    }

    @Override
    public void run(String... args) {
        if (questionRepository.count() == 0) {
            questionRepository.save(new Question("Que es JTA?", "API de transacciones distribuidas"));
            questionRepository.save(new Question("Que patron desacopla productor y consumidor?", "Queue"));
            questionRepository.save(new Question("Que garantiza 2PC?", "Commit atomico entre recursos"));
        }

        if (studentRepository.count() == 0) {
            studentRepository.save(new Student("Ana Perez", "ana.perez@example.com"));
        }
    }
}
