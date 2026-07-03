package com.algomeet.xmpp.chatservice.beans;

import com.algomeet.xmpp.chatservice.document.OfflineMessage;

public record OfflineMessageWithRetention(
        OfflineMessage message,
        Integer retentionDays
) {}
