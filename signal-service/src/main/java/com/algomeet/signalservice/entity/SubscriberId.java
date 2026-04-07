package com.algomeet.signalservice.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Embeddable
@Data
public class SubscriberId implements Serializable {
    private static final long serialVersionUID = 1L;
    
	private UUID userKey;
	
    private UUID subscriberKey;
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SubscriberId that = (SubscriberId) o;
        return Objects.equals(userKey, that.userKey) &&
               Objects.equals(subscriberKey, that.subscriberKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userKey, subscriberKey);
    }
}