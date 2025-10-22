package com.algomeet.chatservice.dto.msgdelete;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
public class MessageDeleteResult {
    private long performedAt;                   // epoch seconds
    private List<String> deletedForEveryone;    // success ids
    private List<String> deletedForMe;          // success ids
    private Map<String, String> failed;         // id -> reason
}
