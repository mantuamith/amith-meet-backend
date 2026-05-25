package com.algomeet.groupservice.model;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Column;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Objects;

import com.algomeet.groupservice.enums.GroupRole;

@Data
@NoArgsConstructor
@Embeddable
public class Member {
	
	public Member(String userKey, String username) {
		this(userKey, username, null, GroupRole.MEMBER, null);
	}
	
	public Member(String userKey, String username, String nickname) {
		this(userKey, username, nickname, GroupRole.MEMBER, null);
	}

	public Member(String userKey, String username, String nickname, GroupRole role) {
		this(userKey, username, nickname, role, null);
	}

	public Member(String userKey, String username, String nickname, GroupRole role, Long memberStartDate) {
		this.userKey = userKey;
		this.username = username;
		this.nickname = nickname;
		this.role = role != null ? role : GroupRole.MEMBER;
		this.memberStartDate = memberStartDate;
	}

    @Column(name = "user_key", nullable = false)
    private String userKey;

    @Column(name = "username")
    private String username;
    
    @Column(name = "nickname")
    private String nickname;
    
    @Enumerated(EnumType.STRING)
    @Column(
        name = "role",
        nullable = false,
        columnDefinition = "varchar(20) default 'MEMBER'"
    )
    private GroupRole role;

    @Column(name = "member_start_date")
    private Long memberStartDate;
    
    /**
     * Unix epoch timestamp (in milliseconds) acting as a temporal deletion threshold for the user.
     * * In-app or Multi-User Chat (MUC) historical messages with a generation timestamp falling 
     * strictly prior to this cutoff value are treated as administratively deleted or cleared 
     * for this specific context session and must be filtered out during synchronized timeline fetches.
     */
    @Column(name = "message_history_cutoff")
    private Long messageHistoryCutoff;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Member)) return false;
        Member member = (Member) o;
        return Objects.equals(userKey, member.userKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userKey);
    }
}
