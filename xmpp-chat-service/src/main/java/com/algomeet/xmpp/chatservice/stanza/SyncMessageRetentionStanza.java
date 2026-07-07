package com.algomeet.xmpp.chatservice.stanza;

import java.util.Objects;

/**
 * Represents an XMPP Message Stanza for Message Retention Synchronization.
 * 
	<message
	    from='<Room ID>@conference.algomeet.app | <User Key>@algomeet.app'
	    to='<User Key>@algomeet.app'
	    type='headline'
	    id='sync-retention-001'>
	
	    <sync-message-retention
	        xmlns='urn:algomeet:retention:0'
	        retention-days='30'/>
	
	</message>
 */
public class SyncMessageRetentionStanza {
	private final String from;
	private final String to;
	private final String type;
	private final String id;
	private final Integer retentionDays;

	private SyncMessageRetentionStanza(Builder builder) {
		this.from = builder.from;
		this.to = builder.to;
		
		if(builder.type == null) {
			builder.type = "headline";
		}
		
		this.type = builder.type;
		this.id = builder.id;
		this.retentionDays = builder.retentiondays;
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Converts the object to a valid XML String according to the custom sync schema.
	 */
	public String toXml() {
		StringBuilder sb = new StringBuilder()
				.append("<message ")
				.append("from='").append(from).append("' ");
		
		// Only append the to attribute if to is provided
		if (to != null && !to.isBlank()) {
			sb.append("to='").append(to).append("' ");
		}
		
		sb.append("type='").append(type).append("' ")
		.append("id='").append(id).append("'>")
		.append("<sync-message-retention xmlns='urn:algomeet:retention:0' ")
		.append("retention-days='").append(retentionDays).append("'/>")
		.append("</message>");

		return sb.toString();
	}

	// --- Getters ---
	public String getFrom() { return from; }
	public String getTo() { return to; }
	public String getType() { return type; }
	public String getId() { return id; }
	public Integer getRetentiondays() { return retentionDays; }

	// --- Builder Class ---
	public static class Builder {
		private String from;
		private String to;
		private String type = "groupchat";
		private String id;
		private Integer retentiondays;

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

		public Builder retentiondays(Integer retentiondays) {
			this.retentiondays = retentiondays;
			return this;
		}

		public SyncMessageRetentionStanza build() {
			Objects.requireNonNull(from, "From JID cannot be null");
			Objects.requireNonNull(id, "Message ID cannot be null");
			Objects.requireNonNull(retentiondays, "Retention days(s) cannot be null");

			return new SyncMessageRetentionStanza(this);
		}
	}
}