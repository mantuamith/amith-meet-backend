package com.algomeet.signalingservice.util;

import java.io.UnsupportedEncodingException;
import java.util.Base64;
import java.util.List;

import org.springframework.util.CollectionUtils;

import com.algomeet.signalingservice.dto.Session;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SessionUtil {
	public static String converToJson(List<Session> sessions) throws UnsupportedEncodingException, JsonProcessingException {
		encodeToBase64(sessions);
		
		// Convert to JSON
		ObjectMapper om = new ObjectMapper();
		return om.writeValueAsString(sessions);
	}
	
	public static List<Session> converToObject(String json) throws UnsupportedEncodingException, JsonProcessingException {
		// Convert to Object
		ObjectMapper om = new ObjectMapper();
		om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		List<Session> sessions = om.readValue(json, new TypeReference<List<Session>>() {});
				
		decodeBase64(sessions);
		
		return sessions;
	}
	
	private static void encodeToBase64(List<Session> sessions) throws UnsupportedEncodingException {
		if(!CollectionUtils.isEmpty(sessions)) {
			for (Session session : sessions) {
				session.setEncryptedSession(encodeToBase64(session.getEncryptedSession()));
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
		
	private static void decodeBase64(List<Session> sessions) throws UnsupportedEncodingException {
		if(!CollectionUtils.isEmpty(sessions)) {
			for (Session session : sessions) {
				session.setEncryptedSession(decodeBase64(session.getEncryptedSession()));			
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
