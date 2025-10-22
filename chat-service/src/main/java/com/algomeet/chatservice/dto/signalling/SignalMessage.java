package com.algomeet.chatservice.dto.signalling;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SignalMessage {
    private String type;     // OFFER, ANSWER, ICE, HANGUP, etc.
    private String to;       // receiver username
    private String toKey;    // receiver UUID
    private String payload;  // SDP offer/answer, ICE candidate, etc.
}
