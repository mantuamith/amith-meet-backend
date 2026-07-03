package com.algomeet.xmpp.chatservice.beans;

import java.util.Objects;
import java.util.UUID;

public record MessageSenderAndReceiver(
        UUID sender,
        UUID receiver
) {
    @Override
    public String toString() {
        return sender + " -> " + receiver;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MessageSenderAndReceiver that)) return false;
        return Objects.equals(sender, that.sender) && 
               Objects.equals(receiver, that.receiver);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sender, receiver);
    }
}