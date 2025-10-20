package com.algomeet.signalingservice.util;

import java.io.UnsupportedEncodingException;
import java.util.Base64;
import java.util.List;

import org.springframework.util.CollectionUtils;

import com.algomeet.signalingservice.dto.GroupSessionRequest;
import com.algomeet.signalingservice.dto.GroupSessionResponse;
import com.algomeet.signalingservice.dto.InboundGroupSessionKey;
import com.algomeet.signalingservice.dto.OutboundSessionRequest;
import com.algomeet.signalingservice.dto.OutboundSessionResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SessionUtil {
	public static String converToJson(List<OutboundSessionRequest> outboundsessions) throws UnsupportedEncodingException, JsonProcessingException {
		encodeToBase64(outboundsessions);
		
		// Convert to JSON
		ObjectMapper om = new ObjectMapper();
		return om.writeValueAsString(outboundsessions);
	}
	
	public static List<OutboundSessionResponse> converToObject(String json) throws UnsupportedEncodingException, JsonProcessingException {
		// Convert to Object
		ObjectMapper om = new ObjectMapper();
		om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		List<OutboundSessionResponse> outboundSessions = om.readValue(json, new TypeReference<List<OutboundSessionResponse>>() {});
				
		decodeBase64(outboundSessions);
		
		return outboundSessions;
	}
	
	private static void encodeToBase64(List<OutboundSessionRequest> sessions) throws UnsupportedEncodingException {
		if(!CollectionUtils.isEmpty(sessions)) {
			for (OutboundSessionRequest groupSession : sessions) {
				groupSession.setEncryptedSession(encodeToBase64(groupSession.getEncryptedSession()));
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
		
	private static void decodeBase64(List<OutboundSessionResponse> sessions) throws UnsupportedEncodingException {
		if(!CollectionUtils.isEmpty(sessions)) {
			for (OutboundSessionResponse groupSession : sessions) {
				groupSession.setEncryptedSession(decodeBase64(groupSession.getEncryptedSession()));			
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
