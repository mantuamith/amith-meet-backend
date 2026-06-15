package com.algomeet.common.dto;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;
import lombok.NoArgsConstructor;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
public class GroupMember implements Comparable<GroupMember> {

    private String userKey;
    private String username;
    private String nickname;
    private String role;
    
    /**
     * TODO: Add to group-service configuration.
     */
    private boolean muted;

    private Long memberStartDate;

    private Long messageHistoryCutoff;

    @Override
    public int hashCode() {
        return userKey != null ? userKey.hashCode() : 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        GroupMember other = (GroupMember) obj;

        return Objects.equals(this.userKey, other.userKey);
    }

    @Override
    public int compareTo(GroupMember other) {
        if (this == other) return 0;
        if (other == null) return 1;

        if (this.userKey == null && other.userKey == null) return 0;
        if (this.userKey == null) return -1;
        if (other.userKey == null) return 1;

        return this.userKey.compareTo(other.userKey);
    }
}