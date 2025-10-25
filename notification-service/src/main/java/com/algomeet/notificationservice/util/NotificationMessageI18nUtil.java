package com.algomeet.notificationservice.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.util.StringUtils;

import com.algomeet.notificationservice.dto.NotificationDto;
import com.algomeet.notificationservice.enums.NotificationType;

import io.jsonwebtoken.lang.Collections;

public class NotificationMessageI18nUtil {
	private static final String TITLE = "_TITLE";
	private static final String BODY = "_BODY";
	
	static final Map<String, String> properties = new HashMap<>();
	
	static {
		properties.put(NotificationType.USER_ONLINE.name() + TITLE, "user-online.title");
		properties.put(NotificationType.USER_ONLINE.name() + BODY , "user-online.body");
		
		properties.put(NotificationType.USER_OFFLINE.name() + TITLE, "user-offline.title");
		properties.put(NotificationType.USER_OFFLINE.name() + BODY , "user-offline.body");
		
		properties.put(NotificationType.AUDIO_CALL.name() + TITLE, "audio-call.title");
		properties.put(NotificationType.AUDIO_CALL.name() + BODY , "audio-call.body");
		
		properties.put(NotificationType.AUDIO_MISSED_CALL.name() + TITLE, "audio-missed-call.title");
		properties.put(NotificationType.AUDIO_MISSED_CALL.name() + BODY , "audio-missed-call.body");
				
		properties.put(NotificationType.DIRECT_MESSAGE.name() + TITLE, "direct-message.title");
		properties.put(NotificationType.DIRECT_MESSAGE.name() + BODY , "direct-message.body");
				
		properties.put(NotificationType.FRIEND_REQUEST.name() + TITLE, "friend-request.title");
		properties.put(NotificationType.FRIEND_REQUEST.name() + BODY , "friend-request.body");
		
		properties.put(NotificationType.FRIEND_REQUEST_ACCEPTED.name() + TITLE, "friend-request-accepted.title");
		properties.put(NotificationType.FRIEND_REQUEST_ACCEPTED.name() + BODY , "friend-request-accepted.body");
		
		properties.put(NotificationType.FRIEND_REQUEST_REJECTED.name() + TITLE, "friend-request-rejected.title");
		properties.put(NotificationType.FRIEND_REQUEST_REJECTED.name() + BODY , "friend-request-rejected.body");
		
		properties.put(NotificationType.GROUP_MESSAGE.name() + TITLE, "group-message.title");
		properties.put(NotificationType.GROUP_MESSAGE.name() + BODY , "group-message.body");
		
		properties.put(NotificationType.MEETING_INVITE.name() + TITLE, "meeting-invite.title");
		properties.put(NotificationType.MEETING_INVITE.name() + BODY , "meeting-invite.body");
				
		properties.put(NotificationType.MEETING_INVITE_ACCEPTED.name() + TITLE, "meeting-invite-accepted.title");
		properties.put(NotificationType.MEETING_INVITE_ACCEPTED.name() + BODY , "meeting-invite-accepted.body");
				
		properties.put(NotificationType.MEETING_REMINDER.name() + TITLE, "meeting-reminder.title");
		properties.put(NotificationType.MEETING_REMINDER.name() + BODY , "meeting-reminder.body");
		
		properties.put(NotificationType.VIDEO_CALL.name() + TITLE, "video-call.title");
		properties.put(NotificationType.VIDEO_CALL.name() + BODY , "video-call.body");
		
		properties.put(NotificationType.VIDEO_MISSED_CALL.name() + TITLE, "video-missed-call.title");
		properties.put(NotificationType.VIDEO_MISSED_CALL.name() + BODY , "video-missed-call.body");		
	}
	
	public static void i18n(NotificationDto notification, String lang) {
		if(lang == null) {
			return;
		}
		
		notification.setTitle(replacePlaceholders(notification.getTitle(), 
				MessageUtil.getMessage(properties.get(notification.getType() + TITLE), Locale.forLanguageTag(lang))));
		
		notification.setBody(replacePlaceholders(notification.getBody(), 
				MessageUtil.getMessage(properties.get(notification.getType() + BODY), Locale.forLanguageTag(lang))));
	}
	
	private static String replacePlaceholders(String message, String messageTranslation) {
		if (!StringUtils.hasLength(messageTranslation)) {
			return message;
		}

		List<String> values = extractPlaceholderValues(message);
		if (!Collections.isEmpty(values)) {
			messageTranslation = replaceMessagePlaceholders(messageTranslation, values);
		}

		return messageTranslation;
	}
	
	private static List<String> extractPlaceholderValues(String str) {
		if (!StringUtils.hasLength(str)) {
			return null;
		}
		
        // Regex to match anything between { and }
        Pattern pattern = Pattern.compile("\\{([^}]*)\\}");
        Matcher matcher = pattern.matcher(str);

        List<String> results = new ArrayList<>();

        while (matcher.find()) {
            results.add(matcher.group(1)); // group(1) = content inside { }
        }
        
        return results;
	}
	
	private static String replaceMessagePlaceholders(String message, List<String> values) {
		if (!StringUtils.hasLength(message) || Collections.isEmpty(values)) {
			return message;
		}
		
		for (int i = 0; i < values.size(); i++) {
			message = message.replace("{" + i + "}", values.get(i));
		}
		
		return message;
	}
}
