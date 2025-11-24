package com.algomeet.signalservice.entity;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Embeddable
@Data
public class KyberPreKeyId implements Serializable {

    private static final long serialVersionUID = 1L;
	private UUID userKey;
    private Integer deviceId;
}