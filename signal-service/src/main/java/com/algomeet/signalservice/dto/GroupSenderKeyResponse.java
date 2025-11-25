package com.algomeet.signalservice.dto;
import java.time.Instant;
import java.util.UUID;

import lombok.Data;

@Data
public class GroupSenderKeyResponse {
    private String groupId;

    private UUID receiverUserKey;
    private Integer receiverDeviceId;

    private UUID senderUserKey;
    private Integer senderDeviceId;

    private String skdmCipher;

    private Instant createdAt;
}
