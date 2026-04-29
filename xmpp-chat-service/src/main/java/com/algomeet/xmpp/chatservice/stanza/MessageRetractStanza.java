package com.algomeet.xmpp.chatservice.stanza;

import java.util.Objects;

/**
 * Represents an XMPP Message Retraction stanza (XEP-0424).
 */
public class MessageRetractStanza {
    private final String from;
    private final String to;
    private final String type;
    private final String id;
    private final String retractedId;
    private final String by;
    private final String stamp;
    private final String reasonText;

    private MessageRetractStanza(Builder builder) {
        this.from = builder.from;
        this.to = builder.to;
        this.type = builder.type;
        this.id = builder.id;
        this.retractedId = builder.retractedId;
        this.by = builder.by;
        this.stamp = builder.stamp;
        this.reasonText = builder.reasonText;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Converts the object to a valid XML String.
     */
    public String toXml() {
        return new StringBuilder()
            .append("<message from='").append(from).append("' ")
            .append("to='").append(to).append("' ")
            .append("type='").append(type).append("' ")
            .append("id='").append(id).append("'>")
            .append("<retracted xmlns='urn:xmpp:message-retract:1' ")
            .append("id='").append(retractedId).append("' ")
            .append("by='").append(by).append("' ")
            .append("stamp='").append(stamp).append("'>")
            .append(reasonText)
            .append("</retracted>")
            .append("</message>")
            .toString();
    }

    // --- Builder Class ---
    public static class Builder {
        private String from;
        private String to;
        private String type = "groupchat"; // Default for MUC
        private String id;
        private String retractedId;
        private String by;
        private String stamp;
        private String reasonText = "This message was retracted."; // Default text

        public Builder from(String from) {
            this.from = from;
            return this;
        }

        public Builder to(String to) {
            this.to = to;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder retractedId(String retractedId) {
            this.retractedId = retractedId;
            return this;
        }

        public Builder by(String by) {
            this.by = by;
            return this;
        }

        public Builder stamp(String stamp) {
            this.stamp = stamp;
            return this;
        }

        public Builder reasonText(String reasonText) {
            this.reasonText = reasonText;
            return this;
        }

        public MessageRetractStanza build() {
            Objects.requireNonNull(from, "From JID cannot be null");
            Objects.requireNonNull(to, "To JID cannot be null");
            Objects.requireNonNull(id, "Message ID cannot be null");
            Objects.requireNonNull(retractedId, "Retracted ID (target) cannot be null");
            Objects.requireNonNull(by, "Retracted 'by' JID cannot be null");
            Objects.requireNonNull(stamp, "Timestamp cannot be null");
            
            return new MessageRetractStanza(this);
        }
    }
}