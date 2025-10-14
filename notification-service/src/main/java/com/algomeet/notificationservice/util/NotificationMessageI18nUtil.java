package com.algomeet.notificationservice.util;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.algomeet.notificationservice.dto.NotificationDto;
import com.algomeet.notificationservice.enums.Language;
import com.algomeet.notificationservice.enums.NotificationType;

public class NotificationMessageI18nUtil {
	private static final String TITLE = "_TITLE";
	private static final String BODY = "_BODY";
	
	static final Map<String, String> properties = new HashMap<>();
	
	static {
		properties.put(NotificationType.USER_ONLINE.name() + TITLE, "notification.user-online.title");
		properties.put(NotificationType.USER_ONLINE.name() + BODY , "notification.user-online.body");
		
		properties.put(NotificationType.USER_OFFLINE.name() + TITLE, "notification.user-offline.title");
		properties.put(NotificationType.USER_OFFLINE.name() + BODY , "notification.user-offline.body");
		
		properties.put(NotificationType.AUDIO_CALL.name() + TITLE, "notification.audio-call.title");
		properties.put(NotificationType.AUDIO_CALL.name() + BODY , "notification.audio-call.body");
		
		properties.put(NotificationType.AUDIO_MISSED_CALL.name() + TITLE, "notification.audio-missed-call.title");
		properties.put(NotificationType.AUDIO_MISSED_CALL.name() + BODY , "notification.audio-missed-call.body");
				
		properties.put(NotificationType.DIRECT_MESSAGE.name() + TITLE, "notification.direct-message.title");
		properties.put(NotificationType.DIRECT_MESSAGE.name() + BODY , "notification.direct-message.body");
				
		properties.put(NotificationType.FRIEND_REQUEST.name() + TITLE, "notification.friend-request.title");
		properties.put(NotificationType.FRIEND_REQUEST.name() + BODY , "notification.friend-request.body");
		
		properties.put(NotificationType.FRIEND_REQUEST_ACCEPTED.name() + TITLE, "notification.friend-request-accepted.title");
		properties.put(NotificationType.FRIEND_REQUEST_ACCEPTED.name() + BODY , "notification.friend-request-accepted.body");
		
		properties.put(NotificationType.GROUP_MESSAGE.name() + TITLE, "notification.group-message.title");
		properties.put(NotificationType.GROUP_MESSAGE.name() + BODY , "notification.group-message.body");
		
		properties.put(NotificationType.MEETING_INVITE.name() + TITLE, "notification.meeting-invite.title");
		properties.put(NotificationType.MEETING_INVITE.name() + BODY , "notification.meeting-invite.body");
				
		properties.put(NotificationType.MEETING_INVITE_ACCEPTED.name() + TITLE, "notification.meeting-invite-accepted.title");
		properties.put(NotificationType.MEETING_INVITE_ACCEPTED.name() + BODY , "notification.meeting-invite-accepted.body");
				
		properties.put(NotificationType.MEETING_REMINDER.name() + TITLE, "notification.meeting-reminder.title");
		properties.put(NotificationType.MEETING_REMINDER.name() + BODY , "notification.meeting-reminder.body");
		
		properties.put(NotificationType.VIDEO_CALL.name() + TITLE, "notification.video.title");
		properties.put(NotificationType.VIDEO_CALL.name() + BODY , "notification.video.body");
		
		properties.put(NotificationType.VIDEO_MISSED_CALL.name() + TITLE, "notification.video-missed-call.title");
		properties.put(NotificationType.VIDEO_MISSED_CALL.name() + BODY , "notification.video-missed-call.body");		
	}
	
	public static void translate(NotificationDto notification, String lang) {
		i18n(notification, 
				lang, 
				MessageUtil.getMessage(properties.get(notification.getType() + TITLE), new Locale(lang)),
				MessageUtil.getMessage(properties.get(notification.getType() + BODY), new Locale(lang)));
	}
	
	public static void i18n(NotificationDto notification, String lang, String title, String body) {
		if(Language.valueOf(lang) == Language.ENGLISH) {
			notification.setTitle(removeBraces(notification.getTitle()));
			notification.setBody(removeBraces(notification.getBody()));
		} else {
			
		}	
	} 

	private static String removeBraces(String message) {
		return message.replace("{", "").replace("}", "");
	}

}
