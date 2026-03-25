package com.algomeet.xmpp.chatservice.document;

import java.time.Instant;

import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.persistence.Id;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Document(collection = "muc_archive")
public class MucArchiveDocument {

    @Id
    private String id; // Internal Mongo ID

    /**
     * Section 3.5: Unique and Stable Stanza ID (XEP-0359)
     * This MUST be unpredictable and unique within the archive.
     */
    @Indexed(unique = true)
    private String stanzaId; 

    /**
     * Section 3.3.2: MUC archives are exposed on the room's bare JID.
     */
    @Indexed
    private String roomJid; 

    /**
     * Section 3: The remote JID that the stanza is from.
     */
    private String senderJid;
    private String senderNickname;

    /**
     * Section 3: The server MUST preserve at least the body and standard attributes.
     * Storing the raw XML is "SHOULD" to preserve extensions.
     */
    private String body;
    private String rawXml; 

    /**
     * Section 3.1: Chronological order must be preserved.
     * Section 4.1.2: Used for 'start' and 'end' filters.
     */
    @Indexed
    private Instant timestamp; 

    /**
     * Section 4.1.4: Used to distinguish if this should be 
     * included in a user's global search.
     */
    private boolean isGroupChat;
}