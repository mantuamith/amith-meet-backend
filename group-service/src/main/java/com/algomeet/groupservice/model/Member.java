package com.algomeet.groupservice.model;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Column;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Objects;

import com.algomeet.groupservice.enums.GroupRole;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Embeddable
public class Member {
	
	public Member(String userKey, String username) {
		this.userKey = userKey;
		this.username = username;
		this.role = GroupRole.MEMBER;
	}

    @Column(name = "user_key", nullable = false)
    private String userKey;

    @Column(name = "username")
    private String username;
    
    @Enumerated(EnumType.STRING)
    @Column(
        name = "role",
        nullable = false,
        columnDefinition = "varchar(20) default 'MEMBER'"
    )
    private GroupRole role;

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
