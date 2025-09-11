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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    @Field("deletedForAll")
    private Boolean deletedForAll = false;                 // sender deleted for everyone?
    @Indexed
    @Field("deletedAt")
    private Long deletedAt;

    // epoch seconds when deleted for all

    // Per-user "delete for me"
    @Field("deletedForUsers")
    private Set<String> deletedForUsers = new HashSet<>(); // usernames who shouldn't see this

    @Field("senderKey")
    private String senderKey;     // UUID string of sender (nullable during transition)

    @Field("receiverKey")
    private String receiverKey;   // UUID string of receiver (nullable during transition)

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

    private CallMetaData callMetaData;

    @Field("failedRecipients")
    private List<String> failedRecipients;

    public boolean isGroupMessage() {
        return groupId != null && !groupId.isEmpty();
    }

    public boolean isVisibleTo(String userId) {
        // 1) hard delete for all
        if (deletedForAll) {
            return false;
        }
        // 2) soft delete only for this user
        if (deletedForUsers != null && deletedForUsers.contains(userId)) {
            return false;
        }
        return true;
    }

    // TODO(migration): when you move “delete for me” to UUIDs, add:
    // @Field("deletedForUserKeys")
    // private Set<String> deletedForUserKeys;



}
