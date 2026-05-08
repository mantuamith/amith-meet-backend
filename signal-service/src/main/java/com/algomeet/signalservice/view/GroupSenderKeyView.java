package com.algomeet.signalservice.view;

import java.time.Instant;
import java.util.UUID;

public interface GroupSenderKeyView {
    String getGroupId();

    UUID getReceiverUserKey();

    Integer getReceiverDeviceId();

    UUID getSenderUserKey();

    Integer getSenderDeviceId();

    Instant getCreatedAt();
    
    Instant getDeletedAt();
}