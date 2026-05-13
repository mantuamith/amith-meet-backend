package com.algomeet.chatservice.dto.messageactions;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReactionsResponse {
    private String messageId;
    private String groupId;
    private Integer totalReactionsCount;
    private List<ReactionEntry> reactions;
}
