package com.algomeet.signalservice.entity;

import java.time.Instant;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(
	    name = "signal_subscribers",
	    uniqueConstraints = {
	        @jakarta.persistence.UniqueConstraint(
	            name = "uc_userKey_subscriberKey",
	            columnNames = { "userKey", "subscriberKey"}
	        )
	    }
	)
public class Subscriber {
    @EmbeddedId
    private SubscriberId id;
    
    private Instant createdAt;
    
    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
    }
}
