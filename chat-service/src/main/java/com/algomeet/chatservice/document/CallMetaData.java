package com.algomeet.chatservice.document;

import com.algomeet.chatservice.model.CallType;

import lombok.Data;

@Data
public class CallMetaData {

    private String roomId;
    private CallType callType;
    private Boolean isMissedCall;
    private Long callDuration;
    private String token;

}
