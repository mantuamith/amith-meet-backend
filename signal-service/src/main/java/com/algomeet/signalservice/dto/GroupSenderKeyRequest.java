package com.algomeet.signalservice.dto;

import java.util.UUID;
import lombok.Data;

@Data
public class GroupSenderKeyRequest {
    private UUID receiverUserKey;
    private Integer receiverDeviceId;

    /** Base64 or hex encoded SKDM ciphertext */
    private String skdmCipher;
}