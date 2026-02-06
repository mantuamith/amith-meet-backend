package com.algomeet.groupservice.model;

import jakarta.persistence.*;
import lombok.Data;

import java.util.HashSet;
import java.util.Set;

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
}
