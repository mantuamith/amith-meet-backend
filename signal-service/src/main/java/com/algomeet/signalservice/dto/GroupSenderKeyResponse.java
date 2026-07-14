package com.algomeet.signalservice.dto;
import java.time.Instant;
import java.util.UUID;

import lombok.Data;

@Data
public class GroupSenderKeyResponse {
    private UUID groupId;

    private UUID receiverUserKey;
    private Integer receiverDeviceId;

    private UUID senderUserKey;
    private Integer senderDeviceId;

    private UUID distributionId;
    
    private String skdmCipher;

    private Instant createdAt;
    
	private Instant deletedAt;
}
