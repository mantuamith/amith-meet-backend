package com.algomeet.signalservice.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import lombok.Data;

@Data
@Entity
@Table(
	    name = "signal_user_devices",
	    indexes = {
	    		@Index(name = "idx_user_device_userKey", columnList = "userKey"),
	    		@Index(name = "idx_user_device_userKey_deviceId", columnList = "userKey, deviceId")
	    },
	    uniqueConstraints = {
	        @jakarta.persistence.UniqueConstraint(
	            name = "uc_userKey_registrationId_identityKey",
	            columnNames = { "userKey", "registrationId", "identityKey" }
	        )
	    }
	)
public class UserDevice {
    @EmbeddedId
    private UserDeviceId id;

    @Column(nullable = false)
    private Integer registrationId;

    /** Identity public Key */
    @Column(nullable = false, length = 200)
    private String identityKey;
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumns({
        @JoinColumn(name = "userKey", referencedColumnName = "userKey"),
        @JoinColumn(name = "deviceId", referencedColumnName = "deviceId")
    })
    private SignedPreKey signedPreKey;
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumns({
        @JoinColumn(name = "userKey", referencedColumnName = "userKey"),
        @JoinColumn(name = "deviceId", referencedColumnName = "deviceId")
    })
    private KyberPreKey kyberPreKey;

    private Instant createdAt;
    private Instant updatedAt;
    
    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}

