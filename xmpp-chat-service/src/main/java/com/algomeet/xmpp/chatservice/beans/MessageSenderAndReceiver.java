package com.algomeet.xmpp.chatservice.beans;

import java.util.UUID;

public record MessageSenderAndReceiver(
        UUID sender,
        UUID receiver
) {}