package com.taller.backend.bd1.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "question")
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "statement", nullable = false, length = 300)
    private String statement;

    @Column(name = "correct_option", nullable = false, length = 100)
    private String correctOption;

    public Question() {
    }

    public Question(String statement, String correctOption) {
        this.statement = statement;
        this.correctOption = correctOption;
    }

    public Long getId() {
        return id;
    }

    public String getStatement() {
        return statement;
    }

    public String getCorrectOption() {
        return correctOption;
    }
}
