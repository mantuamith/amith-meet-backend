package com.algomeet.chatservice.dto.signalling;

import lombok.Data;

@Data
public class SignalChatSyncResponse {
    private String type;         // "REQUEST", "REQUEST_ACK", "OTP_VERIFICATION", "OTP_VERIFICATION_ACK", "OFFER", "ANSWER", "CANDIDATE"
    private String to;           // Target username    
    private String toKey;        // Target identity key or UUID
    private String toDeviceId;   // Receiver device ID   
    private String fromDeviceId; // sender device ID
    private String otp;          // Used for verification
    private String payload;      // WebRTC SDP, ICE candidate object
    private String status;       // "SUCCESS", "ERROR"
    private String errorCode;    // "INVALID_OTP"
}
