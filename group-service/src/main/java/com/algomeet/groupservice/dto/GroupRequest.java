package com.algomeet.groupservice.dto;

import java.util.HashSet;
import java.util.Set;

import lombok.Data;

@Data
public class GroupRequest {

    /**
     * Human-readable name of the group.
     * Example: "Project Alpha Team"
     */
    private String name;

    /**
     * Optional description of the group.
     */
    private String description;

    /**
     * Initial members to be added to the group.
     * <p>
     * Behavior:
     * <ul>
     *   <li>Ignored when {@code emptyGroup = true}</li>
     *   <li>The owner is automatically added if not already included</li>
     * </ul>
     */
    private Set<MemberRequest> members = new HashSet<>();

    /**
     * Flag indicating whether the group should be created without explicitly
     * adding members.
     * <p>
     * When {@code true}:
     * <ul>
     *   <li>The {@code members} list is ignored</li>
     * </ul>
     */
    private boolean emptyGroup;


    private Long createdAt;

//    /**
//     * Optional user key of the group owner.
//     * <p>
//     * Behavior:
//     * <ul>
//     *   <li>If not provided, the group creator is assigned as the owner</li>
//     *   <li>The owner has full administrative privileges</li>
//     *   <li>Used only when {@code emptyGroup = true}</li>
//     * </ul>
//     */
//    private String ownerUserKey;
}
