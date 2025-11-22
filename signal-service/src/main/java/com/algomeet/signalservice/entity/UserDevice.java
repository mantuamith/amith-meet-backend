package com.algomeet.signalservice.entity;

import java.time.Instant;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "signal_user_devices")
public class UserDevice {
    @EmbeddedId
    private UserDeviceId id;

    @Column(nullable = false)
    private Integer registrationId;

    /** Identity public Key */
    @Column(nullable = false, length = 200)
    private String identityKey;
    
    @OneToOne(cascade = CascadeType.ALL)
    @MapsId
    @JoinColumns({
        @JoinColumn(name = "userKey", referencedColumnName = "userKey"),
        @JoinColumn(name = "deviceId", referencedColumnName = "deviceId")
    })
    private SignedPreKey signedPreKey;
    
    @OneToOne(cascade = CascadeType.ALL)
    @MapsId
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

