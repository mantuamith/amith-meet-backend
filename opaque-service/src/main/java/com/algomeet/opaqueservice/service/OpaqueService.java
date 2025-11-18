package com.algomeet.opaqueservice.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.algomeet.opaqueservice.enums.CredentialType;

@Service
public class OpaqueService {
	public void saveRecord(UUID userKey, String record, CredentialType recordType) {		
	}
	
	public String get(UUID userKey, CredentialType recordType) {	
		return null;
	}
}
