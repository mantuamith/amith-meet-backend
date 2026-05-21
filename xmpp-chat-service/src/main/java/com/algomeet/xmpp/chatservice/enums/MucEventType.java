package com.algomeet.xmpp.chatservice.enums;

/**
 * Represents the types of MUC (Multi-User Chat) system events 
 * used within the http://algomeet.app/protocol/system namespace.
 */
public enum MucEventType {
	MEMBER_ACCEPTED_INVITE("member_accepted_invite"),
	MEMBER_ADDED("member_added"),
	MEMBER_REMOVED("member_removed"),
	MEMBER_LEFT("member_left");

	private final String xmlValue;

	MucEventType(String xmlValue) {
		this.xmlValue = xmlValue;
	}

	/**
	 * Returns the exact string representation used in the XML stanza.
	 */
	public String getXmlValue() {
		return xmlValue;
	}

	/**
	 * Helper method to safely find an enum constant from its raw XML string value.
	 */
	public static MucEventType fromXmlValue(String value) {
		for (MucEventType type : MucEventType.values()) {
			if (type.xmlValue.equalsIgnoreCase(value)) {
				return type;
			}
		}
		throw new IllegalArgumentException("Unknown MUC event type: " + value);
	}
}