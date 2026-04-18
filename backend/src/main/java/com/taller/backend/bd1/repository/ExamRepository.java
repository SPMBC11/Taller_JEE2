package com.taller.backend.bd1.repository;

import com.taller.backend.bd1.entity.ExamAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExamRepository extends JpaRepository<ExamAttempt, Long> {
}
