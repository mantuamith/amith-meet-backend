package com.algomeet.chatservice.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document(collection = "messages")
public class Message {

    @Id
    private String id;

    private boolean groupMessage;

    @NotBlank
    String sender;
    @NotBlank// sender user ID
    private String receiver;   // receiver user ID (for direct messages)

    private String groupId;    // group ID (for group messages)

    @NotBlank
    private String content;

    private MessageType type;  // DIRECT or GROUP

    private Instant timestamp = Instant.now(); // auto set if not provided

    public boolean isGroupMessage() {
        return groupId != null && !groupId.isEmpty();
    }
}
