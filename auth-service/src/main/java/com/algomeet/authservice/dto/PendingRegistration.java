package com.algomeet.authservice.dto;

import com.algomeet.authservice.enums.DeviceType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class PendingRegistration {
    private String username;
    private String email;
    private String phone;
    private String password;   // raw at init
    private String deviceId;
    private DeviceType deviceType;
    private Instant createdAt;
}

