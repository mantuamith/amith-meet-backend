package com.algomeet.chatservice.dto;

import com.algomeet.chatservice.document.MessageResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForwardBatchResponse {

    private List<MessageResponse> messages;
}