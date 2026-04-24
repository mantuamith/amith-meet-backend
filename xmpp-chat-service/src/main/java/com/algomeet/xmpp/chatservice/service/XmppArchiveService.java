package com.algomeet.xmpp.chatservice.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.document.MucMessage;
import com.algomeet.xmpp.chatservice.dto.StanzaInfo;
import com.algomeet.xmpp.chatservice.repository.MucMessageRepository;
import com.algomeet.xmpp.chatservice.routing.dispacher.LocalStanzaDispatcher;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;

import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Service for managing XMPP Message Archive Management (MAM) as per XEP-0313.
 * <p>
 * This service handles the persistent storage of MUC stanzas and provides 
 * reactive querying capabilities to allow clients to synchronize chat history.
 * </p>
 * * @author Algomeet Core Team
 * @version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class XmppArchiveService {    
    private final MucMessageRepository repository;
    private final LocalStanzaDispatcher localStanzaDispatcher;

    /**
     * Persists a room event (message or signaling) to the archive.
     *
     * @param xml        The raw XML stanza content.
     * @param info       Metadata extracted from the stanza (ID, Category, Encryption status).
     * @param roomId     The internal ID of the room.
     * @param from       The sender's JID or nickname.
     * @param internalId The unique internal ID (typically a ULID or Snowflake) for database ordering.
     * @return A {@link Mono} containing the saved {@link MucMessage}.
     */
    public Mono<MucMessage> archiveEvent(String xml, StanzaInfo info, String toRoomId, String toMucMember, String from, String internalId) {
        MucMessage event = MucMessage.builder()
                .id(internalId)
                .messageId(info.getMessageId()) // Original client-side ID
                .roomId(toRoomId)
                .from(from)
                .to(toMucMember)
                .stanzaXml(xml)
                .category(info.getCategory())
                .refersTo(info.getTargetId()) // Used for message corrections or replies
                .isE2EE(info.isE2EE())
                .build();

        return repository.save(event);
    }
    
    /**
     * Processes a MAM archive query and streams results back to the client.
     * <p>
     * This method implements the RSM (Result Set Management) pattern to allow 
     * paginated history retrieval. It utilizes a reactive {@code concatMap} to 
     * ensure stanzas are written to the Netty channel in strict chronological order.
     * </p>
     *
     * @param ctx       The Netty channel context for the requesting client.
     * @param roomId    The ID of the room whose history is being requested.
     * @param xml       The raw query stanza containing RSM parameters.
     * @param principal The authenticated user's security principal.
     */
    public void fetchMUCArchive(ChannelHandlerContext ctx, String roomId, String xml, XmppPrincipal principal) {
        // Extract Result Set Management (RSM) parameters
        String afterId = XmppStanzaUtil.getFieldValue(xml, "after-id");
        int maxResults = XmppStanzaUtil.getRsmMax(xml, 50);
        String queryId = XmppStanzaUtil.getAttribute(xml, "id");

        log.debug("MAM Request for Room {}: afterId={}, max={}", roomId, afterId, maxResults);

        // Retrieve messages from MongoDB starting after the specified ID
        repository.findByRoomIdAndIdGreaterThanOrderByIdAsc(
                roomId, 
                afterId != null ? afterId : "", // Ensure non-null for stable Mongo range query
                PageRequest.of(0, maxResults)
        )
        .filter((MucMessage msg) -> 
            // Check for direct private messages with MUC
        	msg.getTo() == null || (msg.getTo() != null && msg.getTo().equalsIgnoreCase(principal.getUserKey()))
        )
        .concatMap((MucMessage msg) -> {
        	        	
            // Wrap the archived stanza in a MAM result container
            String mamResult = String.format(
                    "<message to='%s'>" +
                      "<result xmlns='urn:xmpp:mam:2' %s id='%s'>" +
                        "<forwarded xmlns='urn:xmpp:forward:0'>" +
                          "%s" + // The original archived XML
                        "</forwarded>" +
                      "</result>" +
                    "</message>",
                    principal.getBareJid(),
                    (queryId != null ? "queryid='" + queryId + "'" : ""),
                    msg.getId(),
                    msg.getStanzaXml()
                );

            // Reactive wrapper for Netty write operation to maintain flow control
            return Mono.<Void>create(sink -> {
            	localStanzaDispatcher.dispatchLocally(principal.getUserKey(), principal.getUserKey(), mamResult);               
            });
        })
        .doOnComplete(() -> {
            // Send the final 'fin' stanza to signal the end of the archive stream
            String fin = String.format(
                "<iq type='result' to='%s' %s>" +
                  "<fin xmlns='urn:xmpp:mam:2' complete='true'>" +
                    "<set xmlns='http://jabber.org/protocol/rsm'/>" +
                  "</fin>" +
                "</iq>",
                principal.getBareJid(),
                (queryId != null ? "id='" + queryId + "'" : "")
            );
            
            localStanzaDispatcher.dispatchLocally(principal.getUserKey(), principal.getUserKey(), fin);
        })
        .subscribe();
    }
}