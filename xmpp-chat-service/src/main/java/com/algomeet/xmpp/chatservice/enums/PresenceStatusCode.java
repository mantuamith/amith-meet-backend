package com.algomeet.xmpp.chatservice.enums;

public enum PresenceStatusCode {

	ROOM_CONFIG_CHANGED(104),
	OWN_PRESENCE(110),

	BANNED(301),
	KICKED(307),

	REMOVED_AFFILIATION_CHANGE(321),
	REMOVED_MEMBERS_ONLY(322),
	ROOM_DESTROYED(332);

	private final int code;

	PresenceStatusCode(int code) {
		this.code = code;
	}

	public int getCode() {
		return this.code;
	}
}