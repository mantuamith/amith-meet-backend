package com.algomeet.chatservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebRTCSignalMessage {
    private String type;     // OFFER, ANSWER, ICE, HANGUP, etc.
    private String to;       // receiver username
    private String payload;  // SDP offer/answer, ICE candidate, etc.
}
