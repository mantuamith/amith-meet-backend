package com.algomeet.opaqueservice.service;

import com.algomeet.opaqueservice.enums.CredentialType;

public class OpaqueRedisKeysUtil {
	private static final String REDIS_KEY_REGISTER_SERVER_SEC = "opaque:register:server:sec:%s:s:%s";
	private static final String REDIS_KEY_SECRET_CREDENTIAL_SERVER_SEC = "opaque:secret-credential:server:sec:%s:s:%s";
	private static final String REDIS_KEY_AUTH_MAX_ATTEMPTS_LOCK = "opaque:auth:max-attempts:lock:%s:s:%s";
	
	public static String getRegisterServerSecKey(String userKey, CredentialType type) {
		return String.format(REDIS_KEY_REGISTER_SERVER_SEC, userKey, type.name());
	}
	
	public static String getSecretCredentialServerSecKey(String userKey, CredentialType type) {
		return String.format(REDIS_KEY_SECRET_CREDENTIAL_SERVER_SEC, userKey, type.name());
	}
	
	public static String getAuthMaxAttemptsLockKey(String userKey, CredentialType type) {
		return String.format(REDIS_KEY_AUTH_MAX_ATTEMPTS_LOCK, userKey, type.name());
	}
}
