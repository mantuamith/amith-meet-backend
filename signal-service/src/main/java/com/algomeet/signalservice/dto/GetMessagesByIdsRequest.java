package com.algomeet.signalservice.dto;


import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class GetMessagesByIdsRequest {
	@NotEmpty
	List<UUID> messageIds;

}
