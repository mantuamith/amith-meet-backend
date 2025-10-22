package com.algomeet.chatservice.dto.msgdelete;

import lombok.Data;

import java.util.List;

// STOMP delete command from client
@Data
public class MessageDeleteCommand {
    private List<String> messageIds;
    private boolean deleteForEveryone;  // same meaning
}
