package com.algomeet.signalservice.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GroupSenderKeysRequest {
	@NotNull
	@NotEmpty
	private List<GroupSenderKeyRequest> keys;
}