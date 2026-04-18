package com.taller.backend.bd1.repository;

import com.taller.backend.bd1.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionRepository extends JpaRepository<Question, Long> {
}
