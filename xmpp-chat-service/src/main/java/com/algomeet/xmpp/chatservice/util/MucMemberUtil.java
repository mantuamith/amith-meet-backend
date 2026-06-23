package com.algomeet.xmpp.chatservice.util;

import java.time.Instant;

import com.algomeet.common.dto.GroupMember;
import com.algomeet.common.dto.Group;

public class MucMemberUtil {
	
	public static Instant getHistoryCutoff(Group room, GroupMember member) {
		
		if(member.getMessageHistoryCutoff() != null) {
			return Instant.ofEpochMilli(member.getMessageHistoryCutoff());
		
		} else if(member.getMemberStartDate() != null && !room.isHistoryVisibleToNewMembers()) {			
			return Instant.ofEpochMilli(member.getMemberStartDate());
		} else {
			return Instant.EPOCH;
		}		
	}
}
