package com.algomeet.groupservice.dto;

import java.util.HashSet;
import java.util.Set;

import lombok.Data;

@Data
public class UpdateGroupRequest {
	private String name;
	private String description;
	
    /**
     * Members to be updated to the group.
     * <p>
     * Behavior:
     * <ul>
     *   <li>Ignored when {@code emptyGroup = true}</li>
     *   <li>The owner is automatically added if not already included</li>
     * </ul>
     */
    private Set<MemberRequest> members = new HashSet<>();
    
    /**
     * Number of days chat messages are retained before automatic deletion.
     * 
     * Default -1 meaning no expiration.
     */
    private Integer messageRetentionDays;
}
