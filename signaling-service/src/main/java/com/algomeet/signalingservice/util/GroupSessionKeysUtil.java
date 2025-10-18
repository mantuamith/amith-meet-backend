package com.algomeet.signalingservice.util;

import java.io.UnsupportedEncodingException;
import java.util.Base64;
import java.util.List;

import org.springframework.util.CollectionUtils;

import com.algomeet.signalingservice.dto.GroupSessionRequest;
import com.algomeet.signalingservice.dto.InboundGroupSessionKey;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class GroupSessionKeysUtil {
	public static String converToJson(List<GroupSessionRequest> groupSessions) throws UnsupportedEncodingException, JsonProcessingException {
		encodeToBase64(groupSessions);
		
		// Convert to JSON
		ObjectMapper om = new ObjectMapper();
		return om.writeValueAsString(groupSessions);
	}
	
	public static List<GroupSessionRequest> converToObject(String json) throws UnsupportedEncodingException, JsonProcessingException {
		// Convert to Object
		ObjectMapper om = new ObjectMapper();
		List<GroupSessionRequest> groupSessions	= om.readValue(json, new TypeReference<List<GroupSessionRequest>>() {});
				
		decodeBase64(groupSessions);
		
		return groupSessions;
	}
	
	private static void encodeToBase64(List<GroupSessionRequest> groupSessions) throws UnsupportedEncodingException {
		if(!CollectionUtils.isEmpty(groupSessions)) {
			for (GroupSessionRequest groupSession : groupSessions) {
				groupSession.setEncryptedOutboundSessionKey(encodeToBase64(groupSession.getEncryptedOutboundSessionKey()));
				groupSession.setEncryptedOutboundSession(encodeToBase64(groupSession.getEncryptedOutboundSession()));
				
				if(!CollectionUtils.isEmpty(groupSession.getInboundSessionKeys())) {
					for (InboundGroupSessionKey sessionKey : groupSession.getInboundSessionKeys()) {
						sessionKey.setEncryptedSessionKey(encodeToBase64(sessionKey.getEncryptedSessionKey()));
					}
				}
			}
		}
	}
	
	private static String encodeToBase64(String str) throws UnsupportedEncodingException {
		if (str == null) {
			return null;
		}
		
		byte[] utf8Bytes = str.getBytes("UTF-8");
		Base64.Encoder encoder = Base64.getEncoder();
		return encoder.encodeToString(utf8Bytes);
	}
		
	private static void decodeBase64(List<GroupSessionRequest> groupSessions) throws UnsupportedEncodingException {
		if(!CollectionUtils.isEmpty(groupSessions)) {
			for (GroupSessionRequest groupSession : groupSessions) {
				groupSession.setEncryptedOutboundSessionKey(decodeBase64(groupSession.getEncryptedOutboundSessionKey()));
				groupSession.setEncryptedOutboundSession(decodeBase64(groupSession.getEncryptedOutboundSession()));
				
				if(!CollectionUtils.isEmpty(groupSession.getInboundSessionKeys())) {
					for (InboundGroupSessionKey sessionKey : groupSession.getInboundSessionKeys()) {
						sessionKey.setEncryptedSessionKey(decodeBase64(sessionKey.getEncryptedSessionKey()));
					}
				}
			}
		}
	}
	
	private static String decodeBase64(String str) throws UnsupportedEncodingException {
		if (str == null) {
			return null;
		}
		
		byte[] utf8Bytes = str.getBytes("UTF-8");
		Base64.Decoder decoder = Base64.getDecoder();
		return new String(decoder.decode(utf8Bytes));
	}
}
