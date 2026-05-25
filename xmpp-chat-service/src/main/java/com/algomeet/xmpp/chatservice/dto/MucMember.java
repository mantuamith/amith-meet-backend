package com.algomeet.xmpp.chatservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class MucMember { // or ChatMember
    private String userKey;
    private String username;
    private String nickname;
    private String role;
    private boolean isMuted;
    
    /**
     * Unix epoch timestamp (in milliseconds) indicating when the user joined the group.
     * Used as a structural baseline to prevent members from accessing legacy historical 
     * messages exchanged prior to their active room affiliation date.
     */
    private Long memberStartDate;

    /**
     * Unix epoch timestamp (in milliseconds) acting as a moving temporal clearance threshold.
     * Messages generated strictly prior to this timestamp are treated as cleared or deleted 
     * for this specific member, allowing them to wipe their visibility timeline on demand.
     */
    private Long messageHistoryCutoff;
}