package com.algomeet.xmpp.chatservice.constant;

public class XmppErrorConditions {
    public static final String INTERNAL_SERVER_ERROR = "internal-server-error";
    public static final String SERVICE_UNAVAILABLE = "service-unavailable";
    public static final String BAD_REQUEST = "bad-request";
    public static final String REMOTE_SERVER_NOT_FOUND = "remote-server-not-found";
    public static final String DUPLICATE_KEY_ERROR = "duplicate-key-error";
   
    /**
     * Used when the sent XML is not well-formed or 
     * does not conform to the defined XML schema.
     */
    public static final String BAD_FORMAT = "bad-format";

    /**
     * Specifically for stream-level errors when the XML 
     * is completely unparseable.
     */
    public static final String NOT_WELL_FORMED = "not-well-formed";
    
    /**
     * XEP-0086 / RFC 6120: The sender does not have the permissions 
     * to send a stanza as the specified 'from' JID.
     */
    public static final String FORBIDDEN = "forbidden";
    
}