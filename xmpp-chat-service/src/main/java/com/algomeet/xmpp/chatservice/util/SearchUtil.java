package com.algomeet.xmpp.chatservice.util;

import java.util.Optional;

import com.algomeet.xmpp.chatservice.dto.MucMember;
import com.algomeet.xmpp.chatservice.dto.MucRoomDto;

public class SearchUtil {
	
	public static Optional<MucMember> findMember(MucRoomDto group, String userKey) {
		if (group == null || group.getMembers() == null || userKey == null) {
			return Optional.empty();
		}

		// 1. Create a lightweight dummy search key containing only the ID
		MucMember dummySearchKey = new MucMember();
		dummySearchKey.setUserKey(userKey);

		// 2. Get a view of the set starting from this key (O(log N))
		java.util.SortedSet<MucMember> tailSet = group.getMembers().tailSet(dummySearchKey);

		if (tailSet.isEmpty()) {
			return Optional.empty();
		}

		// 3. Grab the first element in the tail view
		MucMember possibleMatch = tailSet.first();

		// 4. Verify it's an exact match (tailSet returns >= values)
		return possibleMatch.getUserKey().equals(userKey) 
				? Optional.of(possibleMatch) 
						: Optional.empty();
	}
}
