package com.algomeet.notificationservice.constant;

public class Constants {
	
	public final static String SESSION_ATTR_TIME_CONNECTED = "time-connected";
	public final static String SESSION_ATTR_USERNAME = "username";
	public final static String SESSION_ATTR_TENANT_ID = "tenant-id";
	public final static String SESSION_ATTR_DEVICE_TOKEN = "device-token";
	
	public static final String TOKEN_PREFIX = "Bearer";
	public static final String AUTHORIZATION_TOKEN = "Authorization";
	
	public static final String MULTIPLE_RECEIVER_ID_DELIMITER = ";";	
	
	
	public static final String NOTIFICATION_CUSTOM_DATA_NOTIFICATION_ID = "notificationId";	
	public static final String NOTIFICATION_CUSTOM_DATA_NOTIFICATION_TYPE = "notificationType";
	public static final String NOTIFICATION_CUSTOM_DATA_DELIVERY_ACK_REQUIRED = "deliveryAckRequired";
	
	public static final String REDIS_STREAM_MESSAGE_KEY_MESSAGE = "message";
	public static final String REDIS_STREAM_MESSAGE_KEY_TIMESTAMP = "timestamp";
}
