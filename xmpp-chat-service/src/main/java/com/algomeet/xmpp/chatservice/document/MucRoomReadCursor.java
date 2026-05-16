package com.algomeet.xmpp.chatservice.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "muc_room_read_cursors")
@CompoundIndex(name = "idx_cursors_user_room", def = "{'userKey': 1, 'roomId': 1}")
public class MucRoomReadCursor {
    @Id
    private String id; // Structural compound key: String.format("%s_%s", userKey, roomId)

    private String userKey;

    private String roomId;

    private String lastReadMid; // The 'id' of the last message read by the user

    private long lastReadAt;
}