package com.algomeet.xmpp.chatservice.enums;

public enum ParticipantCallStatus {
	PENDING,       // not acted yet
	RINGING,       // currently ringing
	ACCEPTED,      // answered
	REJECTED,      // declined
	CANCELLED,     // caller aborted
	MISSED,        // no answer
	LEFT,          // left after connected
	DISCONNECTED,  // network drop
	RESUMED        // resumed after network drop
}