package com.algomeet.notificationservice.service;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.algomeet.notificationservice.dto.NotificationDto;
import com.algomeet.notificationservice.properties.ApnsProperties;
import com.algomeet.notificationservice.util.ApnsSenderUtil;

@Service
public class ApnsSenderService {
	@Autowired
	private ApnsProperties apnsProperties;
	
	public boolean sendPush(String receiverDeviceToken, NotificationDto notification) throws Exception {
		String p8FilePath = apnsProperties.getP8AuthKeyFilePath(); // download from Apple Developer portal
		String teamId = apnsProperties.getTeamId();
		String keyId = apnsProperties.getP8AuthKeyFileKeyId();
		String bundleId = apnsProperties.getBundleId(); // your iOS app’s bundle ID
		String env = apnsProperties.getEnv();
		ApnsSenderUtil sender = null;	
		
		try {
			// Initialize sender
			sender = new ApnsSenderUtil(p8FilePath, teamId, keyId, bundleId, env);
			// Send notification
			return sender.sendPush(receiverDeviceToken, notification);
		} catch (Exception ex) {
			throw ex;
		}
		finally {
			if (Objects.nonNull(sender)) {
				sender.close();	
			}
		}
	}
}
