package com.algomeet.signalservice.entity;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Embeddable
public class SessionBackupId implements Serializable {
    private static final long serialVersionUID = 1L;
    private UUID userKey;
    
	private Integer deviceId;

	private Integer registrationId;
    
    /** remote user's user key **/
    private UUID remoteUserKey;
    
    /** Remote user's device ID **/
    private Integer remoteDeviceId;
}