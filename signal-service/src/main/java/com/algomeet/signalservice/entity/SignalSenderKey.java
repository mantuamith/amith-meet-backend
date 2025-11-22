package com.algomeet.signalservice.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "signal_sender_keys")
public class SignalSenderKey {
	private Long id;
    private UUID userKey;
    private Integer deviceId;
    private Integer groupId;
    private Integer senderKeyId;
    private String senderPublicKey;
    private String senderPrivateKey;
    private String  senderSymmetricKey;
    private Integer iteration;
    private Instant createdAt;
    private Instant updatedAt;
}
