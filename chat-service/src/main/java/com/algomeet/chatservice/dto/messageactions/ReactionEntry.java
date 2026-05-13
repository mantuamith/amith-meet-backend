package com.algomeet.chatservice.dto.messageactions;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReactionEntry {
    private String username;
    private String userKey;
    private String reaction;
}
