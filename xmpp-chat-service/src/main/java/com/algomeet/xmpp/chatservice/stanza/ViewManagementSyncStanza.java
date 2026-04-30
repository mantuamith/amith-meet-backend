package com.algomeet.xmpp.chatservice.stanza;

import java.util.Objects;

/**
 * Represents a synchronization stanza used to update message visibility 
 * across a user's multiple devices (e.g., hiding a message on mobile and web).
 * * Uses the 'headline' message type to ensure background processing without 
 * affecting unread message counts or triggering notifications.
 */
public class ViewManagementSyncStanza {
    private final String from;
    private final String to;
    private final String id;
    private final String type;
    private final String action;
    private final String room;
    private final String targetId;

    private ViewManagementSyncStanza(Builder builder) {
        this.from = builder.from;
        this.to = builder.to;
        this.id = builder.id;
        this.type = "headline"; // Standard for background sync
        this.action = builder.action;
        this.room = builder.room;
        this.targetId = builder.targetId;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Serializes the synchronization command into a valid XMPP XML string.
     */
    public String toXml() {
        StringBuilder sb = new StringBuilder()
            .append("<message ")
            .append("from='").append(from).append("' ")
            .append("to='").append(to).append("' ")
            .append("id='").append(id).append("' ")
            .append("type='").append(type).append("'>")
            .append("<query xmlns='https://algomeet.app/protocol/view-management'>")
            .append("<item ")
            .append("action='").append(action).append("' ");

        // Only append the room attribute if room is provided
        if (room != null && !room.isBlank()) {
            sb.append("room='").append(room).append("' ");
        }

        sb.append("id='").append(targetId).append("'/>")
          .append("</query>")
          .append("</message>");
          
        return sb.toString();
    }

    // --- Builder Class ---
    public static class Builder {
        private String from;
        private String to;
        private String id;
        private String action = "hide"; // Default action
        private String room;
        private String targetId;

        public Builder from(String from) {
            this.from = from;
            return this;
        }

        public Builder to(String to) {
            this.to = to;
            return this;
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder action(String action) {
            this.action = action;
            return this;
        }

        public Builder room(String room) {
            this.room = room;
            return this;
        }

        /**
         * The ID of the message being hidden/unhidden.
         */
        public Builder targetId(String targetId) {
            this.targetId = targetId;
            return this;
        }

        public ViewManagementSyncStanza build() {
            Objects.requireNonNull(from, "From JID is required");
            Objects.requireNonNull(to, "To JID (usually Bare JID) is required");
            Objects.requireNonNull(id, "Stanza ID is required");
            Objects.requireNonNull(targetId, "Target message ID is required");
            return new ViewManagementSyncStanza(this);
        }
    }
}