package com.algomeet.xmpp.chatservice.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enumeration representing the available view management actions 
 * for chat and channel interface message states.
 */
public enum ConversationViewAction {    
    
    /**
     * Pins a conversation to make it persistently visible within the chat scope.
     */
    PIN("pin"),
    
    /**
     * Removes an active pin assignment from a previously pinned conversation.
     */
    UNPIN("unpin");

    // The lower-case string literal value transmitted over the API boundary
    private final String value;

    /**
     * Internal constructor linking the enum constant to its string payload representation.
     */
    ConversationViewAction(String value) {
        this.value = value;
    }

    /**
     * Returns the raw lower-case value of the enum constant.
     * Annotated with {@link JsonValue} so that Jackson automatically uses this 
     * string value during JSON serialization and deserialization actions.
     */
    @JsonValue
    public String getValue() {
        return value;
    }
}