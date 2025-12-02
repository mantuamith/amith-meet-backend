package com.algomeet.signalservice.entity;

import java.time.Instant;
import java.util.List;

import com.algomeet.signalservice.entity.converter.StringListJsonConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
@Entity
@Table(name = "signal_device_key_backups")
public class DeviceKeyBackup {
	@EmbeddedId
	private DeviceKeyBackupId id;
	
    @Lob
    @Column(columnDefinition = "TEXT", nullable = false)
    private String serializedIdentityKey;	
    
    @Convert(converter = StringListJsonConverter.class)
    @Lob
    @Column(columnDefinition = "TEXT")
    private List<String> serializedPreKeys;
	
    @Lob
    @Column(columnDefinition = "TEXT", nullable = false)
	private String serializedSignedPreKey;

    @Lob
    @Column(columnDefinition = "TEXT", nullable = false)
	private String serializedKyberPreKey;

    @Column(name = "version", length = 10)
    private String version;

    /** Algorithm name, e.g. "AES/GCM/NoPadding" or "AES-CBC". */
    @Column(name = "aesAlg", length = 32)
    private String aesAlg;

    /** Base64-encoded salt for key derivation. */
    @Column(name = "salt", length = 88)
    private String salt;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    private Instant updatedAt;
        
    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    } 
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }   
}
