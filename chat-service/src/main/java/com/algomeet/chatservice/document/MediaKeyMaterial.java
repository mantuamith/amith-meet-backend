package com.algomeet.chatservice.document;

import org.springframework.data.mongodb.core.mapping.Field;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MediaKeyMaterial {
	/** CEK(Content Encryption Key) encrypted using E2EE session (Double Ratchet). */
    @Field("encryptedCek")
    private String encryptedCek;

    /** Algorithm used to encrypt the CEK (e.g., AES-KW, RSA-OAEP). */
    @Field("algorithm")
    private String algorithm;

    /** Key schema version for rotation support. */
    @Field("version")
    private String version;

    /** KDF salt or wrapping IV (if applicable). */
    @Field("salt")
    private String salt;
}
