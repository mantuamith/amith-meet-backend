package com.algomeet.signalservice.entity;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Embeddable
@Data
public class GroupSenderKeyBackupId implements Serializable {
    private static final long serialVersionUID = 1L;
    
	private UUID userKey;	    
	private String groupId;
	private UUID distributionId;
}