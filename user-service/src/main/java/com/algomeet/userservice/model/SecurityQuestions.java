package com.algomeet.userservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sec_questions")
public class SecurityQuestions {
    @Id
    @Column(length = 16)
    private String id;
    
    @Column(length = 255)
    private String question;    
}
