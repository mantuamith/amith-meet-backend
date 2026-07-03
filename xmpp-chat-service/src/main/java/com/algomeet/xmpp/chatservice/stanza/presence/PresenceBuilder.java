package com.algomeet.xmpp.chatservice.stanza.presence;

import java.time.Instant;

import com.algomeet.xmpp.chatservice.enums.UserState;

public abstract class PresenceBuilder<T extends PresenceBuilder<T>> {
    protected String from;
    protected String to;
    protected UserState state;
    protected Long updatedAt;
    protected String domain;

    @SuppressWarnings("unchecked")
    protected T self() { return (T) this; }

    public T from(String from) { this.from = from; return self(); }
    public T state(UserState state) { this.state = state; return self(); }
    public T updatedAt(Long updatedAt) { this.updatedAt = updatedAt; return self(); }
    public T domain(String domain) { this.domain = domain; return self(); }

    protected String getStatusXml() {
        if (state == null) return "";
        return switch (state) {
            case AWAY -> "<show>away</show>";
            case INACTIVE -> "<show>xa</show>";
            case DND -> "<show>dnd</show>";
            default -> "";
        };
    }

    protected String getDelayXml() {
        if (updatedAt == null || updatedAt == 0) return "";
        return String.format("<delay xmlns='urn:xmpp:delay' from='%s' stamp='%s'/>",
                domain, Instant.ofEpochMilli(updatedAt).toString());
    }

    protected String getTypeAttr() {
        return (state == UserState.GONE) ? " type='unavailable'" : "";
    }

    public abstract String build();
}