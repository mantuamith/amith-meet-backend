package com.algomeet.xmpp.chatservice.stanza;

import java.util.List;
import java.util.Objects;

/**
 * Represents an XMPP Read Receipt stanza.
 */
public class MessageSyncReadReceiptStanza {
    private final String from;
    private final String to;
    private final String type;
    private final String id;
    private final String targetMessageId;
    private final List<String> readerUserKeys;

    private MessageSyncReadReceiptStanza(Builder builder) {
        this.from = builder.from;
        this.to = builder.to;
        this.type = builder.type;
        this.id = builder.id;
        this.readerUserKeys = builder.readerUserKeys;
        this.targetMessageId = builder.targetMessageId;
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
          .append("<sync-read-receipts xmlns='urn:algomeet:read:0' ")
          .append(" id='")
          .append(targetMessageId)
        .append("' >");

        if (readerUserKeys != null) {
            for (String userKey : readerUserKeys) {
                sb.append("<reader user-key='")
                  .append(userKey)
                  .append("'/>");
            }
        }

        sb.append("</sync-read-receipts>")
          .append("</message>");

        return sb.toString();
    }

    // --- Builder Class ---
    public static class Builder {

        private String from;
        private String to;
        private String type = "groupchat";
        private String id;
        private String targetMessageId;
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
        
        public Builder targetMessageId(String targetMessageId) {
            this.targetMessageId = targetMessageId;
            return this;
        }

        public Builder readerUserKeys(List<String> readerUserKeys) {
            this.readerUserKeys = readerUserKeys;
            return this;
        }

        public MessageSyncReadReceiptStanza build() {
            Objects.requireNonNull(from, "From JID cannot be null");
            Objects.requireNonNull(readerUserKeys, "Reader userKeys cannot be null");

            return new MessageSyncReadReceiptStanza(this);
        }
    }
}