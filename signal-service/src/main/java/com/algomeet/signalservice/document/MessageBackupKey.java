package com.algomeet.signalservice.document;

import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageBackupKey implements Serializable {
    private static final long serialVersionUID = 1L;
	private UUID userKey;
    private UUID stanzaId;
}