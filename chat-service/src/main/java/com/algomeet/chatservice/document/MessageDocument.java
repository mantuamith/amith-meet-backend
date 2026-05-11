package com.algomeet.chatservice.document;

import com.algomeet.chatservice.dto.UserStatus;
import com.algomeet.chatservice.model.MessageMediaType;
import com.algomeet.chatservice.model.MessageStatus;
import com.algomeet.chatservice.model.MessageType;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.*;

import static java.lang.Boolean.TRUE;

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

    @Transient
    private Double score;

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

    private Long timestamp = System.currentTimeMillis();

    private List<MediaItem> mediaGroup;    // optional media items
    private MessageMetaData metaData;      // reply, reactions, etc.
    private ForwardInfo forwarded;         // forward tracking

    private ReplyContent replyContent;

    private CallMetaData callMetaData;

    private Long msgReadTimeStamp;

    private Long msgDeliveredTimeStamp;

    @Field("deliveredByUsers")
    private List<UserStatus> deliveredByUsers = new ArrayList<>();

    @Field("readByUsers")
    private List<UserStatus> readByUsers = new ArrayList<>();

    @Field("failedRecipients")
    private List<String> failedRecipients;
    
    private List<EncrytionMetadata> encryptionMetadata;

//    public boolean isGroupMessage() {
//        return groupId != null && !groupId.isEmpty();
//    }

    public boolean isVisibleTo(String userId) {
        // 1) hard delete for all
        if (TRUE.equals(deletedForAll)) {
            return false;
        }
        // 2) soft delete only for this user
        final Set<String> hiddenFor = this.deletedForUsers;
        if (hiddenFor != null && hiddenFor.contains(userId)) {
            return false;
        }
        return true;
    }

    public boolean isReadBy(String userId) {
        if (userId == null) {
            return false;
        }
        if (userId.equals(sender)) {
            return true;
        }
        if (isGroupMessage()) {
            Optional<UserStatus> existing = getReadByUsers()
                    .stream()
                    .filter(u -> u.getUsername().equals(userId))
                    .findFirst();
            return readByUsers != null && existing.isPresent();
        }
        return userId.equals(receiver) && status == MessageStatus.READ;
    }

    public boolean isDeliveredTo(String userId) {
        if (userId == null) {
            return false;
        }
        if (userId.equals(sender)) {
            return true;
        }
        if (isGroupMessage()) {
            Optional<UserStatus> existing = getReadByUsers()
                    .stream()
                    .filter(u -> u.getUsername().equals(userId))
                    .findFirst();
            return deliveredByUsers != null && existing.isPresent();
        }
        return userId.equals(receiver) && (status == MessageStatus.DELIVERED || status == MessageStatus.READ);
    }

    public void markReadBy(String userId, Long ts) {
        if (getReadByUsers() == null) {
            setReadByUsers(new ArrayList<>());
        }

        Optional<UserStatus> existing = getReadByUsers()
                .stream()
                .filter(u -> u.getUsername().equals(userId))
                .findFirst();

        if (existing.isPresent()) {
            existing.get().setTimestamp(ts);
        } else {
            getReadByUsers().add(new UserStatus(userId, ts));
        }
    }

    public void markDeliveredTo(String userId, Long ts) {
        if (deliveredByUsers == null) {
            setDeliveredByUsers(new ArrayList<>());
        }

        Optional<UserStatus> existing = getDeliveredByUsers()
                .stream()
                .filter(u -> u.getUsername().equals(userId))
                .findFirst();

        if (existing.isPresent()) {
            existing.get().setTimestamp(ts);
        } else {
            deliveredByUsers.add(new UserStatus(userId, ts));
        }
    }

    // TODO(migration): when you move “delete for me” to UUIDs, add:
    // @Field("deletedForUserKeys")
    // private Set<String> deletedForUserKeys;



}
