package com.algomeet.userservice.repository;

import com.algomeet.userservice.model.SecurityQuestions;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecurityQuestionRepository extends JpaRepository<SecurityQuestions, String> {
}
