package com.algomeet.xmpp.chatservice.stanza.message;

import java.util.Objects;

/**
 * Standard XMPP <message/> stanza builder.
 *
 * Supports:
 * - chat
 * - groupchat
 * - headline
 * - normal
 *
 * Includes optional deleted flag for tombstone / retracted messages.
 *
 * Example normal message:
 * <message from='alice@app' to='bob@app' type='chat' id='m1'>
 *   <body>Hello</body>
 * </message>
 *
 * Example deleted message:
 * <message from='alice@app' to='bob@app' type='chat' id='m1'>
 *   <deleted>true</deleted>
 * </message>
 */
public class StandardMessageStanza {

    public static final String TYPE_CHAT = "chat";
    public static final String TYPE_GROUPCHAT = "groupchat";
    public static final String TYPE_NORMAL = "normal";
    public static final String TYPE_HEADLINE = "headline";

    private final String from;
    private final String to;
    private final String id;
    private final String type;
    private final String body;
    private final boolean deleted;

    private StandardMessageStanza(Builder builder) {
        this.from = builder.from;
        this.to = builder.to;
        this.id = builder.id;
        this.type = builder.type;
        this.body = builder.body;
        this.deleted = builder.deleted;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Converts object into valid XMPP XML stanza.
     *
     * Uses StringBuilder for low GC pressure in high-throughput systems.
     */
    public String toXml() {
        StringBuilder xml = new StringBuilder(256);

        xml.append("<message ")
           .append("from='").append(from).append("' ")
           .append("to='").append(to).append("' ")
           .append("type='").append(type).append("' ")
           .append("id='").append(id).append("'>");

        if (deleted) {
            xml.append("<deleted>true</deleted>");
        } else if (body != null && !body.isEmpty()) {
            xml.append("<body>").append(escapeXml(body)).append("</body>");
        }

        xml.append("</message>");

        return xml.toString();
    }

    /**
     * Minimal XML escaping for message body safety.
     */
    private String escapeXml(String value) {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }

    // ----------------------------------------------------
    // Builder
    // ----------------------------------------------------
    public static class Builder {

        private String from;
        private String to;
        private String id;
        private String type = TYPE_CHAT;
        private String body;
        private boolean deleted = false;

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

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder body(String body) {
            this.body = body;
            return this;
        }

        public Builder deleted(boolean deleted) {
            this.deleted = deleted;
            return this;
        }

        /**
         * Validates required fields before build.
         */
        public StandardMessageStanza build() {
            Objects.requireNonNull(from, "From JID cannot be null");
            Objects.requireNonNull(to, "To JID cannot be null");
            Objects.requireNonNull(id, "Message ID cannot be null");
            Objects.requireNonNull(type, "Message type cannot be null");

            if (!deleted && (body == null || body.isBlank())) {
                throw new IllegalArgumentException(
                    "Body cannot be null/blank when message is not deleted"
                );
            }

            return new StandardMessageStanza(this);
        }
    }
}