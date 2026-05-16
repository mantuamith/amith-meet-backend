package com.algomeet.xmpp.chatservice.stanza;

import java.util.List;
import java.util.Objects;

/**
 * Represents an XMPP Read Receipt stanza.
 */
public class MessageReadReceiptStanza {

    private final String from;
    private final String to;
    private final String type;
    private final String id;
    private final List<String> readerUserKeys;

    private MessageReadReceiptStanza(Builder builder) {
        this.from = builder.from;
        this.to = builder.to;
        this.type = builder.type;
        this.id = builder.id;
        this.readerUserKeys = builder.readerUserKeys;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Converts the object to a valid XMPP XML String.
     */
    public String toXml() {
        StringBuilder sb = new StringBuilder()
                .append("<message from='").append(from).append("' ");

        if (to != null && !to.isBlank()) {
            sb.append("to='").append(to).append("' ");
        }

        sb.append("type='").append(type).append("' ")
          .append("id='").append(id).append("'>")
          .append("<read-receipts xmlns='urn:algomeet:xmpp:read:0'>");

        if (readerUserKeys != null) {
            for (String userKey : readerUserKeys) {
                sb.append("<reader user-key='")
                  .append(userKey)
                  .append("'/>");
            }
        }

        sb.append("</read-receipts>")
          .append("</message>");

        return sb.toString();
    }

    // --- Builder Class ---
    public static class Builder {

        private String from;
        private String to;
        private String type = "groupchat";
        private String id;
        private List<String> readerUserKeys;

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

        public Builder readerUserKeys(List<String> readerUserKeys) {
            this.readerUserKeys = readerUserKeys;
            return this;
        }

        public MessageReadReceiptStanza build() {
            Objects.requireNonNull(from, "From JID cannot be null");
            Objects.requireNonNull(readerUserKeys, "Reader userKeys cannot be null");

            return new MessageReadReceiptStanza(this);
        }
    }
}