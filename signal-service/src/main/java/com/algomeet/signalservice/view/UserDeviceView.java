package com.algomeet.signalservice.view;

import java.time.Instant;

import com.algomeet.signalservice.entity.UserDeviceId;

public interface UserDeviceView {
	UserDeviceId getId();
    Integer getRegistrationId();
    String getIdentityKey();
    Instant getCreatedAt();    
    Instant getUpdatedAt();
}
