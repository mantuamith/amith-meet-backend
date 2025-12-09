package com.algomeet.chatservice.dto.msgsearch;

import com.algomeet.chatservice.document.MessageResponse;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SearchMessageResponse {
    private MessageResponse message; // your standard mapped response
    private Double score;            // $text score (optional)
    private String snippet;          // optional future use (basic = message.text)
}
