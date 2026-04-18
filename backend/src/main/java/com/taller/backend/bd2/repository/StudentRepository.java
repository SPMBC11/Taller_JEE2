package com.taller.backend.bd2.repository;

import com.taller.backend.bd2.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentRepository extends JpaRepository<Student, Long> {
}
