package com.algomeet.signalservice.repository.projection;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class ConversationStorageStats {
    private Long totalSize;
    private Long messageCount;

    // Standard getters/setters and a default constructor are vital
    public ConversationStorageStats() {} 
}