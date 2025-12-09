// Delete request over REST
package com.algomeet.chatservice.dto.msgdelete;
import lombok.Data;

import java.util.List;

@Data
public class MessageDeleteRequest {
    private List<String> messageIds;
    private boolean deleteForEveryone;  // true = delete for all, false = only for me
}

