package com.algomeet.notificationservice.util;

import java.io.File;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.algomeet.notificationservice.dto.NotificationDto;
import com.algomeet.notificationservice.enums.ApnsEnv;
import com.eatthepath.pushy.apns.ApnsClient;
import com.eatthepath.pushy.apns.ApnsClientBuilder;
import com.eatthepath.pushy.apns.PushNotificationResponse;
import com.eatthepath.pushy.apns.auth.ApnsSigningKey;
import com.eatthepath.pushy.apns.util.SimpleApnsPushNotification;
import com.eatthepath.pushy.apns.util.TokenUtil;
import com.eatthepath.pushy.apns.util.concurrent.PushNotificationFuture;

import jakarta.validation.ValidationException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ApnsSenderUtil {

	private final ApnsClient apnsClient;
	private final String bundleId;

	/**
	 * @param p8FilePath path to the .p8 authentication key file from Apple
	 * @param teamId Apple Developer Team ID
	 * @param keyId Key ID of the .p8 file
	 * @param bundleId App’s bundle identifier (target app on device)
	 * @param env(target environment sandbox or production)
	 */
	public ApnsSenderUtil(String p8FilePath, String teamId, String keyId, String bundleId, String env) throws Exception {
		this.bundleId = bundleId;

		this.apnsClient = new ApnsClientBuilder()
				.setApnsServer(ApnsEnv.valueOf(env.toUpperCase()).equals(ApnsEnv.SANDBOX) 
						? ApnsClientBuilder.DEVELOPMENT_APNS_HOST 
								: ApnsClientBuilder.PRODUCTION_APNS_HOST) 
				.setSigningKey(ApnsSigningKey.loadFromPkcs8File(new File(p8FilePath), teamId, keyId))
				.build();
	}
	// chat, meeting, 
	public boolean sendPush(String receiverDeviceToken, NotificationDto notification) throws Exception {
		if (Objects.isNull(notification)) {
			throw new ValidationException("Notification is null");
		}

		if (!StringUtils.hasLength(receiverDeviceToken)) {
			throw new ValidationException("Receiver device token is null");
		}

		// Build payload JSON
		StringBuilder payloadBuilder = new StringBuilder();
		payloadBuilder.append("{\"aps\":{\"alert\":{\"title\":\"")
		.append(notification.getTitle())
		.append("\",\"body\":\"")
		.append(notification.getBody())
		.append("\"},\"sound\":\"default\"}");

		// Add custom data if present
		if (!(CollectionUtils.isEmpty(notification.getData()))) {
			for (Map.Entry<String, Object> entry : notification.getData().entrySet()) {
				payloadBuilder.append(",\"")
				.append(entry.getKey())
				.append("\":")
				.append(((entry.getValue() instanceof String) ? "\"" + entry.getValue()  + "\"" : entry.getValue()));
			}
		}

		payloadBuilder.append("}");

		String payload = payloadBuilder.toString();
		// APNs requires sanitized token
		String token = TokenUtil.sanitizeTokenString(receiverDeviceToken);

		// Build notification
		SimpleApnsPushNotification pushNotification =
				new SimpleApnsPushNotification(token, bundleId, payload);

		// Send notification
		PushNotificationFuture<SimpleApnsPushNotification, PushNotificationResponse<SimpleApnsPushNotification>> future =
				apnsClient.sendNotification(pushNotification);

		// Wait for response
		PushNotificationResponse<SimpleApnsPushNotification> response = future.get(5, TimeUnit.SECONDS);

		if (response.isAccepted()) {
			// Message delivered to apple notification service
			return true;
		} else {			
			log.error("Error sending notification to APNs {}", response);
			
			return false;
		}
	}

	public void close() throws Exception {
		apnsClient.close();
	}
}