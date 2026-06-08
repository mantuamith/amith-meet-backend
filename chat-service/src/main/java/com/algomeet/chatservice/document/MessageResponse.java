package com.algomeet.chatservice.document;

import com.algomeet.chatservice.dto.MessageStatusUpdate;
import com.algomeet.chatservice.dto.UserStatus;
import com.algomeet.chatservice.model.MessageMediaType;
import com.algomeet.chatservice.model.MessageStatus;
import com.algomeet.chatservice.model.MessageType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageResponse {
    private String id;
    private String from;
    private String to; // for group chats this is the group id
    private Long timestamp;
    private MessageType type;
    private MessageMediaType messageMediaType;;
    private MessageStatus status;
    private String content;
    private String text;
    private List<MediaItem> mediaGroup;
    private ReplyContent replyContent;
    private MessageMetaData meta;
    private ForwardInfo forwarded;
    private Integer nMessages;
    private List<String> failedRecipients;
    private String clientMessageId;

    private String fromKey;  // UUID string
    private String toKey;    // UUID string
    
    private List<EncrytionMetadata> encryptionMetadata;

    private Long msgReadTimeStamp;

    private Long msgDeliveredTimeStamp;

    private List<UserStatus> readByUsers;
    private List<UserStatus> deliveredByUsers;

}
