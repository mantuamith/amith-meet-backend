package com.algomeet.xmpp.chatservice.listener;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.algomeet.xmpp.chatservice.dto.E2eeEvent;
import com.algomeet.xmpp.chatservice.routing.dispacher.LocalStanzaDispatcher;
import com.algomeet.xmpp.chatservice.util.JidUtil;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>Subscriber/Listener responsible for receiving E2EE and Signal-related events 
 * synchronized across the Algomeet server cluster.</p>
 * * <p>In a distributed deployment, security events (like Signal Protocol key updates 
 * or session resets) may be generated on one node but need to be processed by another 
 * node where the target user's WebSocket session is physically active. This listener 
 * intercepts those events from the Redis Pub/Sub fabric, de-serializes the 
 * {@link E2eeEvent} payload, and triggers local handling.</p>
 * * <p><b>Execution Flow:</b></p>
 * <ol>
 * <li><b>Intercept:</b> Receives raw JSON from the Redis cluster topic.</li>
 * <li><b>De-serialize:</b> Converts the JSON string into an {@code E2eeEvent} object.</li>
 * <li><b>Identify:</b> Extracts the target user's key to determine if their 
 * active session resides on <i>this</i> specific server instance.</li>
 * <li><b>Dispatch:</b> Forwards the event to the {@link LocalStanzaDispatcher} 
 * for delivery to the connected client.</li>
 * </ol>
 * * @author Algomeet Core Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class E2eeEventMessageListener {	
    private final JidUtil jidUtil;

    /** Dispatcher used to route stanzas to local Netty channels/sessions. */
    private final LocalStanzaDispatcher localStanzaDispatcher;

    public void onMessage(String rawMessage, String channel) {
        log.debug("Received E2EE event message on channel [{}]: {}", channel, rawMessage);
        
        E2eeEvent event = convertToObject(rawMessage, E2eeEvent.class);

        if (event != null && event.getSubscribers() != null) {
            log.info("Processing cluster-synchronized E2EE event from user: {}", event.getSourceUserKey());
            
            // 1. Compose the inner <bundle> content once to reuse for all subscribers
            String bundlePayload = composeBundlePayload(event);

            for (String subscriberKey : event.getSubscribers()) {
                // 2. Wrap in the full IQ Result structure
                // Note: Use a unique ID or the one from the original request if available
                String stanzaId = UUID.randomUUID().toString();
                
                String fullIq = String.format(
                    "<iq from='%s' to='%s' type='result' id='%s'>" +
                    "  <pubsub xmlns='http://jabber.org/protocol/pubsub'>" +
                    "    <items node='urn:xmpp:omemo:2:bundles:%s'>" +
                    "      <item id='current'>%s</item>" +
                    "    </items>" +
                    "  </pubsub>" +
                    "</iq>",
                    jidUtil.getBareJid(event.getSourceUserKey()), // Assuming this is the 'from' JID
                    jidUtil.getBareJid(subscriberKey),            // The recipient
                    stanzaId,
                    event.getDeviceId(),      // Ensure your E2eeEvent DTO has deviceId
                    bundlePayload
                );

                localStanzaDispatcher.dispatchLocally(subscriberKey, event.getSourceUserKey(), fullIq);
            }            
        }
    }

    /**
     * Composes the OMEMO bundle XML based on the SignalEvent data.
     */
    private String composeBundlePayload(E2eeEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append("<bundle xmlns='urn:xmpp:omemo:2'>");
        
        // Add your custom AlgoMeet metadata
        sb.append("  <metadata xmlns='com.algomeet.e2ee' actionType='").append(event.getActionType()).append("'>");
        sb.append("    <deviceId>").append(event.getDeviceId()).append("</deviceId>");
        sb.append("  </metadata>");
 
        sb.append("</bundle>");
        return sb.toString();
    }

    /**
     * <p>Utility to de-serialize JSON strings into specific Java types.</p>
     * * <p><b>Implementation Note:</b> This uses {@code findAndRegisterModules()} 
     * to ensure support for Java 8+ Date/Time types (like Instant or OffsetDateTime) 
     * which are standard for security event timestamps.</p>
     * * @param json The JSON input string.
     * @param t    The class type to map the JSON to.
     * @param <T>  The generic type of the destination object.
     * @return The de-serialized object, or null if an error occurred during parsing.
     */
    private <T> T convertToObject(String json, Class<T> t) {
        try {
            // Consideration: Injecting a pre-configured ObjectMapper Bean 
            // would be more performant than inline instantiation.
            ObjectMapper mapper = new ObjectMapper().findAndRegisterModules(); 
            return mapper.readValue(json, t);
        } catch (Exception ex) {
            log.error("Failed to de-serialize E2ee event message. Payload: {}, Error: {}", 
                json, ex.getMessage());
        }
        return null;
    }
}