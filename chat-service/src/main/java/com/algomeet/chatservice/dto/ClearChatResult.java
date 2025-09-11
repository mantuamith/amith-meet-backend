// REST response
package com.algomeet.chatservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data @AllArgsConstructor
public class ClearChatResult {
    private long affected;      // how many messages were marked "deleted for me"
    private String contactId;
}
