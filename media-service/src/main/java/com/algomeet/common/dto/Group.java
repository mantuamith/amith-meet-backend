package com.algomeet.common.dto;

import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import lombok.Data;

/**
 * TODO: This will be move to common library project.
 */

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class Group {
    private UUID id;

    private String name;

    @JsonDeserialize(as = TreeSet.class)
    private SortedSet<GroupMember> members = new TreeSet<>();    
    
    /**
     * TODO: Add to group-service configuration.
     */
    private boolean historyVisibleToNewMembers = true;
}