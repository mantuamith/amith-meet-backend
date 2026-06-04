package com.algomeet.xmpp.chatservice.util;

import java.time.Instant;

import com.algomeet.xmpp.chatservice.dto.MucMember;

public class MucMemberUtil {
	
	public static Instant getHistoryCutoff(MucMember member) {
		
		if(member.getMessageHistoryCutoff() != null) {
			return Instant.ofEpochMilli(member.getMessageHistoryCutoff());
		
		} else if(member.getMemberStartDate() != null) {			
			return Instant.ofEpochMilli(member.getMemberStartDate());
		} else {
			return Instant.EPOCH;
		}		
	}
	
	public static Long getHistoryCutoffLong(MucMember member) {
		if(member.getMessageHistoryCutoff() != null) {
			return Instant.ofEpochMilli(member.getMessageHistoryCutoff()).toEpochMilli();
		
		} else if(member.getMemberStartDate() != null) {			
			return Instant.ofEpochMilli(member.getMemberStartDate()).toEpochMilli();
		} else {
			return Instant.EPOCH.toEpochMilli();
		}		
	}
}
