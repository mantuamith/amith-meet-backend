package com.algomeet.userservice.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.*;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_sec_question_answers")
public class UserSecurityQuestionAnswers {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    
    /**
     * Used to link to user_profile table
     */
    private UUID userProfileId;

    // Instead of storing sec_question_id as String, 
    // we map it to the SecurityQuestions entity.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sec_question_id", referencedColumnName = "id") 
    private SecurityQuestions securityQuestion;

    private String answer;

    private String type;
    
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}