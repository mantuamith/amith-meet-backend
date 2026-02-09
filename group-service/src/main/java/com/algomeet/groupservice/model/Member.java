package com.algomeet.groupservice.model;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Column;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Embeddable
public class Member {

    @Column(name = "user_key", nullable = false)
    private String userKey;

    @Column(name = "username")
    private String username;

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
