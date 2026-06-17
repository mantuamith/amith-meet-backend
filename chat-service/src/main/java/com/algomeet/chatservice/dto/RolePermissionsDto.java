package com.algomeet.chatservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class RolePermissionsDto {
    private boolean sendMessageHistory;
}
