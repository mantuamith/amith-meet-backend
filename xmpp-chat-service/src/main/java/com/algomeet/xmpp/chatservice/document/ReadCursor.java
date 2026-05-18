package com.algomeet.xmpp.chatservice.document;

import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "read_cursors")
@CompoundIndex(name = "idx_cursors_user_sender", def = "{'userKey': 1, 'senderKey': 1}")

public class ReadCursor {
    @Id
    private String id; // format: <senderKey>_<recipientKey>

    private String userKey;

    private String senderKey;

    private UUID lastReadMid; // The 'id' of the last message read by the user

    private long lastReadAt;
}