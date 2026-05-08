package com.algomeet.chatservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class GroupMessageReceiptResponse {

    private String groupId;

    private String messageId;

    private Integer deliveredCount;

    private Integer readCount;

    private List<UserStatus> delivered;

    private List<UserStatus> read;
}