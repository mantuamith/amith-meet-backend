package com.algomeet.xmpp.chatservice.document;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Document(collection = "sm_buffer_messages") // Fixed typo and aligned with class name
@CompoundIndex(
	    name = "sm_playback_idx",
	    def = "{'smSid' : 1, 'seq' : 1}"
	)
public class SmBufferMessage {
    @Id
    private String id;          

    @Indexed
    private String smSid;    
    
    /**
     * Monotonic XEP-0198 outbound sequence number.
     *
     * Enables deterministic ordered replay after resume.
     */
    @NotNull
    @Field("seq")
    private String seq;
        
    @Size(max = 66560, message = "XML stanza is too large") 
    private String stanzaXml;   

    // MongoDB uses this field to calculate the expiration.
    // Ensure this is set to Instant.now() or new Date() when saving.
    // It is used also for XEP-0203 Delayed Delivery stamp
    @Builder.Default
    private Instant createdAt = Instant.now(); 
}