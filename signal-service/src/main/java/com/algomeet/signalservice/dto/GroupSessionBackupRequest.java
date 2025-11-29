package com.algomeet.signalservice.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class GroupSessionBackupRequest {

    @NotBlank
    private String groupId;

    @NotNull
    private UUID distributionId;

    private Integer deviceId;

    private UUID remoteUserKey;

    private Integer remoteDeviceId;

    @NotBlank
    @Size(max = 350)
	@Pattern(
		    regexp = "^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$",
		    message = "{invalid-base64-format}"
		)
    private String serializedSenderKey;

    private String aesAlg;

    private String version;

    private String salt;
}
