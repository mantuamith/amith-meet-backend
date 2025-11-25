package com.algomeet.signalservice.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "signal_group_sender_keys")
public class GroupSenderKey {
	/** Auto-generated primary key */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
	private UUID receiverUserKey;
    
    @Column(nullable = false)
    private Integer receiverDeviceId;
    
    @Column(nullable = false)
	private String groupId;
	
    @Column(nullable = false)
	private UUID senderUserKey;	
    
    @Column(nullable = false)
	private Integer senderDeviceId;	
	
	/** Sender key distribution message */
    @Column(nullable = false, length = 2800)
	private String skdmCipher;
    		
	private Instant createdAt;
	
    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
    }
}
