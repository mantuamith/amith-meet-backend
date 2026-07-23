package com.algomeet.signalservice.dto;
import java.util.List;

import lombok.Data;

@Data
public class GroupSenderKeysResponse {   
    private List<GroupSenderKeyResponse> keys;
}
