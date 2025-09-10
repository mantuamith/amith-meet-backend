package com.algomeet.notificationservice.properties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
public class ApnsProperties {
	
	@Value("${apple-apns.env:}")
	private String env;
	
	@Value("${apple-apns.team-id:}")
	private String teamId;
	
	@Value("${apple-apns.bundle-id:}")
	private String bundleId;
	
	@Value("${apple-apns.p8-auth-key-file-path:}")
	private String p8AuthKeyFilePath;
	
	@Value("${apple-apns.p8-auth-key-file-key-id:}")
	private String p8AuthKeyFileKeyId;
}
