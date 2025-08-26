package com.algomeet.chatservice.document;

import com.algomeet.chatservice.model.MessageMediaType;
import com.algomeet.chatservice.model.MessageStatus;
import com.algomeet.chatservice.model.MessageType;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@Document(collection = "messages")
public class MessageDocument {

    @Id
    private String id;

    @Field("isGroupMessage")
    private boolean groupMessage;

    private String clientMessageId;



    private String sender;   // sender user ID (from)

    @NotBlank
    private String receiver; // receiver user ID (to)

    private String groupId;  // non-null if groupMessage is true

    @NotBlank
    private String content;  // optional if mediaGroup is used

    private MessageType type;               // text, media, etc.
    private MessageMediaType messageMediaType; // image, video, etc.
    private MessageStatus status;          // SENT, DELIVERED, etc.

    private Instant timestamp = Instant.now();

    private List<MediaItem> mediaGroup;    // optional media items
    private MessageMetaData metaData;      // reply, reactions, etc.
    private ForwardInfo forwarded;         // forward tracking

    @Field("failedRecipients")
    private List<String> failedRecipients;

    public boolean isGroupMessage() {
        return groupId != null && !groupId.isEmpty();
    }


}
