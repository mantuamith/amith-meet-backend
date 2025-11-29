package com.algomeet.signalservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class GroupSenderKeyBackupUpdateRequest {
    @NotBlank
    @Size(max = 300)
    private String serializedSkdm;

    @NotBlank
    private String version;
    
    @NotBlank
    private String aesAlg;
    @NotBlank
    private String salt;
}