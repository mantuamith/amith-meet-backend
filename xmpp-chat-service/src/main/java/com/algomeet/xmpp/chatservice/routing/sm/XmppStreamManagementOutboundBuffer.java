package com.algomeet.xmpp.chatservice.routing.sm;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>Tracks outbound stanzas sent from the server to the client to ensure reliable delivery.</p>
 * 
 * <p>This component implements the storage logic for <b>XEP-0198: Stream Management</b>. 
 * It maintains a mapping between the server's outgoing sequence number (h) and the 
 * internal Stanza ID. This allows the server to reconcile which messages were 
 * successfully received by the client when an {@code <a h='...' />} acknowledgment 
 * is received.</p>
 * 
 * <p>Key Responsibilities:</p>
 * <ul>
 *     <li>Buffering Stanza IDs sent to the client until acknowledged.</li>
 *     <li>Identifying "confirmed" messages to update database delivery status.</li>
 *     <li>Preventing memory leaks by purging acknowledged data from the session buffer.</li>
 * </ul>
 * 
 * @author Algomeet Core Team
 * @see <a href="https://xmpp.org/extensions/xep-0198.html">XEP-0198: Stream Management</a>
 */
@Component
@Slf4j
public class XmppStreamManagementOutboundBuffer {

    /**
     * Map structure: userKey -> (Sequence Number (h) -> Stanza ID)
     * Using ConcurrentHashMap to ensure thread-safety across multiple active Netty channels.
     */
    private final Map<String, Map<Long, String>> sessionBuffers = new ConcurrentHashMap<>();

    /**
     * Initializes an outbound tracking buffer for a new XMPP session.
     * 
     * @param userKey The Jabber ID of the user/device session.
     */
    public void register(String userKey) {
        sessionBuffers.put(userKey, new ConcurrentHashMap<>());
        log.debug("Stream Ack tracking initialized for userKey: {}", userKey);
    }

    /**
     * Records a stanza as "in-flight". Maps the current server-to-client 
     * sequence number to the specific Stanza ID.
     * 
     * @param userKey The Jabber ID associated with the stream.
     * @param sequenceH The outgoing sequence number (h) assigned to this stanza.
     * @param stanzaId The unique ID of the stanza (message, iq, or presence).
     */
    public void track(String userKey, long sequenceH, String stanzaId) {
        Map<Long, String> buffer = sessionBuffers.get(userKey);
        if (buffer != null) {
            buffer.put(sequenceH, stanzaId);
        }
    }

    /**
     * Reconciles the session buffer based on the client's reported handled count.
     * 
     * <p>When a client sends an {@code <a h='X' />}, it confirms receipt of all 
     * stanzas up to and including sequence X. This method collects those IDs 
     * for further processing (e.g., DB updates) and removes them from memory.</p>
     * 
     * @param userKey The Jabber ID associated with the stream.
     * @param h The 'handled' count reported by the client.
     * @return A list of Stanza IDs that have been successfully delivered and acknowledged.
     */
    public List<String> acknowledgeUpTo(String userKey, long h) {
        Map<Long, String> buffer = sessionBuffers.get(userKey);
        List<String> acknowledgedStanzaIds = new ArrayList<>();

        if (buffer != null) {
            Iterator<Map.Entry<Long, String>> iterator = buffer.entrySet().iterator();
            
            while (iterator.hasNext()) {
                Map.Entry<Long, String> entry = iterator.next();
                // If the sequence number is less than or equal to the client's 'h'
                if (entry.getKey() <= h) {
                    acknowledgedStanzaIds.add(entry.getValue());
                    iterator.remove();
                }
            }
            
            log.trace("Cleared {} stanzas from buffer for userKey: {} (up to h={})", 
                acknowledgedStanzaIds.size(), userKey, h);
        }
        
        return acknowledgedStanzaIds;
    }

    /**
     * Cleans up all tracking data for a disconnected or terminated session.
     * 
     * @param userKey The Jabber ID of the session to unregister.
     */
    public void unregister(String userKey) {
        sessionBuffers.remove(userKey);
        log.debug("Stream Ack tracking removed for userKey: {}", userKey);
    }

    /**
     * Global cleanup performed during application shutdown to release memory.
     */
    @PreDestroy
    public void shutdown() {
        sessionBuffers.clear();
        log.info("XmppStreamManagementOutboundBuffer cleared during shutdown.");
    }
}