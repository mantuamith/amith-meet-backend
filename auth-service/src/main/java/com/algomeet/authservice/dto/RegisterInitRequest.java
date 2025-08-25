// dto/RegisterInitRequest.java
package com.algomeet.authservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterInitRequest {
    @NotBlank private String username;
    @Email private String email;           // either email or phone (at least one)
    private String phone;

    @NotBlank private String password;     // raw; will be BCrypted at commit

    @NotBlank private String deviceId;
    @NotBlank private String deviceType;   // WEB | ANDROID | IOS | DESKTOP

    // optional profile-ish fields
    private String country;
    private String region;
    private String city;
    private Double latitude;
    private Double longitude;
}
