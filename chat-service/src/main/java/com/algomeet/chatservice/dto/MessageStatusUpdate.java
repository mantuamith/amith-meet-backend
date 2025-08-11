package com.algomeet.chatservice.dto;

import lombok.Data;
import java.util.List;

@Data
public class MessageStatusUpdate {
    private List<String> messageIds;
}
