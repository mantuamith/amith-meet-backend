// src/main/java/com/algomeet/contactservice/i18n/MessageResolver.java
package com.algomeet.contactservice.i18n;

import com.algomeet.contactservice.enums.ResponseCode;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

@Component
public class MessageResolver {
    private final MessageSource ms;

    public MessageResolver(MessageSource ms) {
        this.ms = ms;
    }

    /** Resolve by explicit message key (preferred for endpoint-specific success texts). */
    public String msg(String key, Object... args) {
        return ms.getMessage(key, args, key, LocaleContextHolder.getLocale());
    }

    /** Resolve generic message for a ResponseCode (fallback). */
    public String msg(ResponseCode rc, Object... args) {
        return ms.getMessage(rc.getDefaultMsgKey(), args, rc.getDefaultMsgKey(), LocaleContextHolder.getLocale());
    }
}
