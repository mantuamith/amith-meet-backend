package com.algomeet.signalservice.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import lombok.Data;

@Data
@Entity
@Table(
	    name = "signal_group_sender_keys",
	    indexes = {
	        @Index(
	            name = "idx_receiver_user_device_group",
	            columnList = "receiver_user_key, receiver_device_id, group_id"
	        ),
	        @Index(
		            name = "idx_sender_user_device_group",
		            columnList = "sender_user_key, sender_device_id, group_id"
		        )
	        ,
	        @Index(
		            name = "idx_receiver_user_device",
		            columnList = "receiver_user_key, receiver_device_id"
		        )
	    }
	)
public class GroupSenderKey {
	@EmbeddedId
	private GroupSenderKeyId id;
	
	/** Sender key distribution message */
    @Column(nullable = false, length = 3000)
	private String skdmCipher;
    		
	private Instant createdAt;
	
    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
    }
}
