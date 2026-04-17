package com.algomeet.xmpp.chatservice.util;

import java.util.Set;

import com.algomeet.xmpp.chatservice.enums.UserState;
import com.algomeet.xmpp.chatservice.session.model.UserSession;

public class UserStateUtil {
	/**
	 * Arbitrates the "Global State" for a user with multiple active sessions.
	 * Priority: DND > ACTIVE > AWAY > INACTIVE > GONE.
	 * * @param sessions A set of all concurrent sessions for a single user.
	 * @return The highest priority UserState.
	 */
	public static UserState determineOverallState(Set<UserSession> sessions) {
		if (sessions.stream().anyMatch(s -> s.getState() == UserState.DND)) return UserState.DND;
		if (sessions.stream().anyMatch(s -> s.getState() == UserState.ACTIVE)) return UserState.ACTIVE;
		if (sessions.stream().anyMatch(s -> s.getState() == UserState.AWAY)) return UserState.AWAY;
		if (sessions.stream().anyMatch(s -> s.getState() == UserState.INACTIVE)) return UserState.INACTIVE;
		return UserState.GONE;
	}
}
