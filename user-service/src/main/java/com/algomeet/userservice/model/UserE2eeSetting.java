package com.algomeet.userservice.model;

import java.util.UUID;

import com.algomeet.userservice.converter.Argon2ConfigConverter;
import com.algomeet.userservice.dto.Argon2Config;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_ee2e_settings")
public class UserE2eeSetting {
	/**
	 * Used to link to users table
	 */
    @Id   
    private UUID userKey;
        
    /**
     * Used to enable or disable the sessions backup synchronization, and etc.
     */
    @Column(nullable = false)
    private Boolean autoSyncEnabled = true; // Java-level default
    
    /**
     * Used as flag if PIN was configured by the user.
     */
    @Column(nullable = false)
    private Boolean pinConfigured; 
    
    /**
     * Used as flag if passcode was configured by the user.
     */
    @Column(nullable = false)
    private Boolean passcodeConfigured;  
    
    /**
     * PIN Argon2 parameters configuration
     */
    @Column(columnDefinition = "VARCHAR(512)")
    @Convert(converter = Argon2ConfigConverter.class)
    private Argon2Config argon2Config;
    
    /**
     * Ensure default is applied before saving if not explicitly set.
     */
    @PrePersist
    public void prePersist() {
        if (autoSyncEnabled == null) {
            autoSyncEnabled = true;
        }
        
        if (pinConfigured == null) {
        	pinConfigured = false;
        }
        
        if (passcodeConfigured == null) {
        	passcodeConfigured = false;
        }
    } 
}
