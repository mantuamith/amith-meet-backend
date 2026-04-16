package com.algomeet.xmpp.chatservice.stanza.presence;

public class DirectedPresenceBuilder extends PresenceBuilder<DirectedPresenceBuilder> {
    private boolean isGroup = false;

    public DirectedPresenceBuilder asGroup() {
        this.isGroup = true;
        return this;
    }

    @Override
    public String build() {
        String mucNamespace = isGroup ? " <x xmlns='http://jabber.org/protocol/muc'/>" : "";
        
        return String.format("<presence from='%s' to='%s'%s>%s%s %s</presence>",
                from, 
                to, 
                getTypeAttr(), 
                getStatusXml(), 
                mucNamespace, 
                getDelayXml());
    }
}