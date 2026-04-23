package com.algomeet.xmpp.chatservice.enums;

public enum CallStatus {
    INITIATED,     // created
    RINGING,       // callee being notified
    ACTIVE,        // connected
    MISSED,        // unanswered timeout
    REJECTED,      // explicitly declined
    CANCELLED,     // caller cancelled before answer
    ENDED,         // completed normally
    FAILED         // technical failure
}
