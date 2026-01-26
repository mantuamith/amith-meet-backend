package com.algomeet.mediaservice.util;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

@Component
public class MessageUtil {

    private static MessageSource messageSource;

    // Spring injects MessageSource here
    public MessageUtil(MessageSource messageSource) {
    	MessageUtil.messageSource = messageSource;
    }

    /**
     * Get a localized message by key.
     *
     * @param key message key (from messages.properties)
     * @param args optional message arguments
     * @return localized message string
     */
    public static String getMessage(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }
}


