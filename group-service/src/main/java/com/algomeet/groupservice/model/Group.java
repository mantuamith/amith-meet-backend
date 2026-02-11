package com.algomeet.groupservice.model;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "chat_groups")
public class Group {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ElementCollection
    @CollectionTable(
        name = "chat_group_members",
        joinColumns = @JoinColumn(name = "group_id")
    )
    private Set<Member> members = new HashSet<>();
    
    // owner is always a member
    @Column(name = "owner_user_key", updatable = false)
    private String ownerUserKey;
    
    private String createdBy;
    
    @Column(
    		name = "date_created",
    		nullable = false,
    		updatable = false,
    		columnDefinition = "timestamp with time zone default now()"
    		)
    @CreationTimestamp
    private Instant dateCreated;
}
