package com.algomeet.chatservice.document;

import com.algomeet.chatservice.model.MessageMediaType;
import com.algomeet.chatservice.model.MessageStatus;
import com.algomeet.chatservice.model.MessageType;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@Document(collection = "messages")
@CompoundIndexes({
        // For queries: { sender: A, receiver: B } sorted by timestamp/_id
        @CompoundIndex(name = "idx_sender_receiver_ts_id",
                def  = "{'sender': 1, 'receiver': 1, 'timestamp': -1, '_id': -1}"),
        // For the reversed branch in $or: { receiver: A, sender: B }
        @CompoundIndex(name = "idx_receiver_sender_ts_id",
                def  = "{'receiver': 1, 'sender': 1, 'timestamp': -1, '_id': -1}")
})
public class MessageDocument {

    @Id
    private String id;

    @Field("isGroupMessage")
    private boolean groupMessage;

    private String clientMessageId;

    private Boolean deletedForAll;                 // sender deleted for everyone?
    @Indexed
    private Long deletedAt;                        // epoch seconds when deleted for all

    // Per-user "delete for me"
    private java.util.Set<String> deletedForUsers; // usernames who shouldn't see this



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
