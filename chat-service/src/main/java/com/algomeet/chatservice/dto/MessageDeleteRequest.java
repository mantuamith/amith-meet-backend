// Delete request over REST
package com.algomeet.chatservice.dto;
import lombok.Data;

@Data
public class MessageDeleteRequest {
    private String messageId;
    private boolean deleteForEveryone;  // true = delete for all, false = only for me
}

