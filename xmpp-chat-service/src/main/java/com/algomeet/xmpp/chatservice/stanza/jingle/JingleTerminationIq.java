package com.algomeet.xmpp.chatservice.stanza.jingle;

import java.util.Objects;

public class JingleTerminationIq {
	public static final String REASON_TIMEOUT = "timeout";
    private final String from;
    private final String to;
    private final String id;
    private final String sid;
    private final String action;
    private final String reason;

    private JingleTerminationIq(Builder builder) {
        this.from = builder.from;
        this.to = builder.to;
        this.id = builder.id;
        this.sid = builder.sid;
        this.action = "session-terminate";
        this.reason = builder.reason;
    }

    // Static entry point for the builder
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Converts the object to a valid XML String.
     * Uses StringBuilder for performance in high-traffic environments.
     */
    public String toXml() {
        return new StringBuilder()
            .append("<iq type='set' ")
            .append("from='").append(from).append("' ")
            .append("to='").append(to).append("' ")
            .append("id='").append(id).append("'>")
            .append("<jingle xmlns='urn:xmpp:jingle:1' ")
            .append("action='").append(action).append("' ")
            .append("sid='").append(sid).append("'>")
            .append("<reason><").append(reason).append("/></reason>")
            .append("</jingle></iq>")
            .toString();
    }

    // --- Builder Class ---
    public static class Builder {
        private String from;
        private String to;
        private String id;
        private String sid;
        private String reason = "timeout"; // Default value

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

        public Builder sid(String sid) {
            this.sid = sid;
            return this;
        }

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        /**
         * Validates the object state before construction.
         */
        public JingleTerminationIq build() {
            Objects.requireNonNull(from, "From JID cannot be null");
            Objects.requireNonNull(to, "To JID cannot be null");
            Objects.requireNonNull(id, "Stanza ID cannot be null");
            Objects.requireNonNull(sid, "Session ID (sid) cannot be null");
            return new JingleTerminationIq(this);
        }
    }
}