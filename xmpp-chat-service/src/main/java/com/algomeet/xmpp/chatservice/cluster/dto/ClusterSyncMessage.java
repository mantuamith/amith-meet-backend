package com.algomeet.xmpp.chatservice.cluster.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClusterSyncMessage { 
	private String id;            // Stanza/ message ID
	private String to;            // Target Userkey/ JID
	private String from;          // Sender Userkey/ JID
	private String payload;       // The XMPP XML or JSON

    
    @Builder.Default
    private long timestamp = System.currentTimeMillis(); 
}