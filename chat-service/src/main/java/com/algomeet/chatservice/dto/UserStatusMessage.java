package com.algomeet.chatservice.dto;

import com.algomeet.chatservice.model.AppStatus;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserStatusMessage {
    private AppStatus status;
}
