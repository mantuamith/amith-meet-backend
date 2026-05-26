package com.algomeet.xmpp.chatservice.dto;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class MucMember implements Comparable<MucMember> {

    private String userKey;
    private String username;
    private String nickname;
    private String role;
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

        MucMember other = (MucMember) obj;

        return Objects.equals(this.userKey, other.userKey);
    }

    @Override
    public int compareTo(MucMember other) {
        if (this == other) return 0;
        if (other == null) return 1;

        if (this.userKey == null && other.userKey == null) return 0;
        if (this.userKey == null) return -1;
        if (other.userKey == null) return 1;

        return this.userKey.compareTo(other.userKey);
    }
}