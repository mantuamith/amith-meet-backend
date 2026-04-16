package com.algomeet.xmpp.chatservice.stanza.presence;

public class SelfPresenceBuilder extends PresenceBuilder<SelfPresenceBuilder> {
    private String id;

    public SelfPresenceBuilder id(String id) {
        this.id = id;
        return this;
    }

    @Override
    public String build() {
        return String.format("<presence id='%s'%s>%s %s</presence>",
                id, 
                getTypeAttr(), 
                getStatusXml(), 
                getDelayXml());
    }
}