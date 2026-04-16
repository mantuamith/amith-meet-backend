package com.algomeet.groupservice.model;

import java.time.Instant;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;
import lombok.Data;

import com.algomeet.groupservice.enums.GroupRole;

@Data
@Entity
@Table(name = "chat_groups")
public class Group {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(name = "description")
    private String description;

    @ElementCollection
    @CollectionTable(
        name = "chat_group_members",
        joinColumns = @JoinColumn(name = "group_id"),
        indexes = {
        		@jakarta.persistence.Index(name = "idx_group_members_group_id", columnList = "group_id"),
        		@jakarta.persistence.Index(name = "idx_group_members_user_key", columnList = "user_key")
        }
    )
    private Set<Member> members = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "chat_group_role_permissions",
        joinColumns = @JoinColumn(name = "group_id")
    )
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "group_role")
    private Map<GroupRole, RolePermissions> rolePermissions = new EnumMap<>(GroupRole.class);
    
    // owner is always a member
    @Column(name = "owner_user_key", updatable = false)
    private String ownerUserKey;
    
    private String createdBy;

    @Column(name = "invite_code")
    private String inviteCode;
    
    @Column(
    		name = "date_created",
    		nullable = false,
    		updatable = false,
    		columnDefinition = "timestamp with time zone default now()"
    		)
    @CreationTimestamp
    private Instant dateCreated;
}
