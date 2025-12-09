package com.algomeet.chatservice.dto.signalling;

import com.algomeet.chatservice.document.CallMetaData;
import lombok.AllArgsConstructor;
import lombok.Data;

/** Event pushed to the counterparty when call metadata changes */
@Data @AllArgsConstructor
public class CallMetaUpdatedEvent {
    private String messageId;
    private String byUser;          // who updated
    private String contactId;       // who should display it as “other side”
    private CallMetaData callMeta;  // new metadata
    private long updatedAt;         // epoch seconds
}
