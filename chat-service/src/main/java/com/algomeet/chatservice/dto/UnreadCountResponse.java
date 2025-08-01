package com.algomeet.chatservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UnreadCountResponse {
    private String contactId;
    private int nMessages;
}
