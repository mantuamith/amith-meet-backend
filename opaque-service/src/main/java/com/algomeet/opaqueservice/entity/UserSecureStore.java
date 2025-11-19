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
    @Column(name = "secret_key", nullable = false, length = 512)
    private String secretKey;    
}