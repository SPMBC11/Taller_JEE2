package com.taller.backend.bd2.repository;

import com.taller.backend.bd2.entity.Grade;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GradeRepository extends JpaRepository<Grade, Long> {
}
