package com.algomeet.opaqueservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Entity
@Table(name = "user_secure_store")
public class UserSecureStore {
    @EmbeddedId
    private UserSecureStoreId id;

    @Lob
    @Column(name = "rec", nullable = false, length = 512)
    private String rec;
    
    @Lob
    @Column(name = "master_secret_key", nullable = false, length = 512)
    private String masterSecretKey;   
    
	/** Algorithm name, e.g. "AES/GCM/NoPadding" or "AES-CBC". */
    @Column(length = 32)
    private String algorithm;
    
    /** Encryption algorithm version (for compatibility, e.g. "v1", "v2"). */
    @Column(length = 10)
    private String version;

    /** Base64-encoded salt value for key derivation (optional but recommended). */
    @Column(length = 88)
    private String salt;
}