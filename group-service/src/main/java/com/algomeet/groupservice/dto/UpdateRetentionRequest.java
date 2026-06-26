package com.algomeet.groupservice.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class UpdateRetentionRequest {
    @Min(value = -1, message = "Retention days must be -1 (infinite) or greater than 0")
    private Integer messageRetentionDays;
}