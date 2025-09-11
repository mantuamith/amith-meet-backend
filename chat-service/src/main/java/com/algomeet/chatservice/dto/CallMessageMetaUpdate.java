package com.algomeet.chatservice.dto;

import com.algomeet.chatservice.document.CallMetaData;
import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class CallMessageMetaUpdate {

    private String messageId;
    private CallMetaData callMetaData;
}
