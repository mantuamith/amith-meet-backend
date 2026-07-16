package com.algomeet.signalservice.view;

import java.time.Instant;
import java.util.UUID;

public interface GroupSenderKeyView {
    UUID getGroupId();

    UUID getReceiverUserKey();

    Integer getReceiverDeviceId();

    UUID getSenderUserKey();

    Integer getSenderDeviceId();

    UUID getDistributionId();
    
    Instant getCreatedAt();
    
    Instant getDeletedAt();
}