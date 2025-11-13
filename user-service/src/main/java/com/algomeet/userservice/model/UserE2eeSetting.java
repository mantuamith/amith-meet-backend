package com.algomeet.userservice.model;

import java.util.UUID;

import jakarta.persistence.Column;
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
     * Contains an encrypted key that used to decrypt the backup sessions, encrypted chat history, and etc.
     * This key can be decrypted using PIN, user password and etc.
     * 
     * 
     */
    @Column(length = 512)
    private String autoSyncKey; 
    
    /**
     * Used to enable or disable the sessions backup synchronization, and etc.
     */
    @Column(nullable = false)
    private Boolean autoSyncEnabled = true; // Java-level default

    /**
     * Ensure default is applied before saving if not explicitly set.
     */
    @PrePersist
    public void prePersist() {
        if (autoSyncEnabled == null) {
            autoSyncEnabled = true;
        }
    } 
}
