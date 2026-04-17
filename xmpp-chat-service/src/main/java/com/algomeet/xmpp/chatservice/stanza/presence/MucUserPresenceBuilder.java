package com.algomeet.xmpp.chatservice.stanza.presence;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.List;

/**
 * Builder for MUC User Presence stanzas as defined in XEP-0045.
 * Includes support for XEP-0203 Delayed Delivery and MUC Status Codes.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MucUserPresenceBuilder {

    private String fromRoomJid;
    private String nickname;
    private String toJid;
    private String affiliation;
    private String role;
    private String targetJid;
    private String reason;
    private String show;
    private String status;
    private String updatedAt;
    private final List<Integer> statusCodes = new ArrayList<>();

    public static MucUserPresenceBuilder create() {
        return new MucUserPresenceBuilder();
    }

    public MucUserPresenceBuilder from(String roomJid, String nickname) {
        this.fromRoomJid = roomJid;
        this.nickname = nickname;
        return this;
    }

    public MucUserPresenceBuilder to(String toJid) {
        this.toJid = toJid;
        return this;
    }

    public MucUserPresenceBuilder affiliation(String affiliation) {
        this.affiliation = affiliation;
        return this;
    }

    public MucUserPresenceBuilder role(String role) {
        this.role = role;
        return this;
    }

    public MucUserPresenceBuilder show(String show) {
        this.show = show;
        return this;
    }

    public MucUserPresenceBuilder status(String status) {
        this.status = status;
        return this;
    }

    public MucUserPresenceBuilder updatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
        return this;
    }

    /**
     * Adds a MUC status code (e.g., 110 for self-presence).
     */
    public MucUserPresenceBuilder statusCode(int code) {
        this.statusCodes.add(code);
        return this;
    }

    public MucUserPresenceBuilder targetJid(String jid) {
        this.targetJid = jid;
        return this;
    }

    public MucUserPresenceBuilder reason(String reason) {
        this.reason = reason;
        return this;
    }

    public String build() {
        StringBuilder xml = new StringBuilder();
        
        // Presence Opening Tag
        xml.append("<presence from='").append(fromRoomJid).append("/").append(nickname).append("'");
        if (toJid != null) xml.append(" to='").append(toJid).append("'");
        xml.append(">");

        // MUC User Extension
        xml.append("<x xmlns='http://jabber.org/protocol/muc#user'>");
        
        // Item element
        xml.append("<item");
        if (affiliation != null) xml.append(" affiliation='").append(affiliation).append("'");
        if (role != null) xml.append(" role='").append(role).append("'");
        if (targetJid != null) xml.append(" jid='").append(targetJid).append("'");
        
        if (reason != null && !reason.isEmpty()) {
            xml.append("><reason>").append(reason).append("</reason></item>");
        } else {
            xml.append("/>");
        }

        // Status codes (inside the <x> extension)
        for (Integer code : statusCodes) {
            xml.append("<status code='").append(code).append("'/>");
        }
        
        xml.append("</x>");

        // Delayed Delivery
        if (updatedAt != null) {
            xml.append("<delay xmlns='urn:xmpp:delay' stamp='").append(updatedAt).append("'/>");
        }

        // Optional presence status elements
        if (show != null) xml.append("<show>").append(show).append("</show>");
        if (status != null) xml.append("<status>").append(status).append("</status>");
        
        xml.append("</presence>");
        
        return xml.toString();
    }
}