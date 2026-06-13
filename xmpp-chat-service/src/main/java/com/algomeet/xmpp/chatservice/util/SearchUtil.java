package com.algomeet.xmpp.chatservice.util;

import java.util.Optional;

import com.algomeet.common.dto.GroupMember;
import com.algomeet.common.dto.Group;

public class SearchUtil {
	
	public static Optional<GroupMember> findMember(Group group, String userKey) {
		if (group == null || group.getMembers() == null || userKey == null) {
			return Optional.empty();
		}

		// 1. Create a lightweight dummy search key containing only the ID
		GroupMember dummySearchKey = new GroupMember();
		dummySearchKey.setUserKey(userKey);

		// 2. Get a view of the set starting from this key (O(log N))
		java.util.SortedSet<GroupMember> tailSet = group.getMembers().tailSet(dummySearchKey);

		if (tailSet.isEmpty()) {
			return Optional.empty();
		}

		// 3. Grab the first element in the tail view
		GroupMember possibleMatch = tailSet.first();

		// 4. Verify it's an exact match (tailSet returns >= values)
		return possibleMatch.getUserKey().equals(userKey) 
				? Optional.of(possibleMatch) 
						: Optional.empty();
	}
}
