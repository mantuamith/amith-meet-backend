package com.algomeet.chatservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebRTCSignalResponse {
    private String type;     // OFFER, ANSWER, ICE, HANGUP
    private String from;     // sender username
    private String payload;  // actual signaling data (SDP, ICE, etc.)
}
