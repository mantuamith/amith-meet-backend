package com.algomeet.xmpp.chatservice.stanza;

import java.util.Objects;

/**
 * Represents an XMPP stanza used to notify users when a message is pinned or unpinned.
 * Uses the 'headline' message type to target a specific recipient connection context.
 */
public class PinStanza {
	private final String from;
	private final String to;
	private final String id;
	private final String type;
	private final String action;
	private final String targetId;

	private PinStanza(Builder builder) {
		this.from = builder.from;
		this.to = builder.to;
		this.id = builder.id;
		this.type = "headline";
		this.action = builder.action;
		this.targetId = builder.targetId;
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Serializes the pin command into a valid XMPP XML string.
	 */
	public String toXml() {
		StringBuilder sb = new StringBuilder()
				.append("<message ")
				.append("id='").append(id).append("' ");
		
		if (from != null && !from.isBlank()) {
			sb.append("from='").append(from).append("' ");
		}
		
		if (to != null && !to.isBlank()) {
			sb.append("to='").append(to).append("' ");
		}
		
		sb.append("type='").append(type).append("'>")
		  .append("<pin xmlns='urn:xmpp:algomeet:pin:0' ")
		  .append("action='").append(action).append("' ")
		  .append("id='").append(targetId).append("' />")
		  .append("</message>");

		return sb.toString();
	}

	// --- Builder Class ---
	public static class Builder {
		private String from;
		private String to;
		private String id;
		private String action = "pin"; // Default action matching your example
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

		/**
		 * The ID of the specific chat message being pinned or unpinned.
		 */
		public Builder targetId(String targetId) {
			this.targetId = targetId;
			return this;
		}

		public PinStanza build() {
			Objects.requireNonNull(id, "Stanza ID is required");
			Objects.requireNonNull(action, "Action is required");
			Objects.requireNonNull(targetId, "Target message ID is required");
			return new PinStanza(this);
		}
	}
}