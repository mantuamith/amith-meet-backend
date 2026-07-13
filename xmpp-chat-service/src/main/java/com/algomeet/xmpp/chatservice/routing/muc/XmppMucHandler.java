package com.algomeet.xmpp.chatservice.routing.muc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.algomeet.common.dto.Group;
import com.algomeet.common.dto.GroupMember;
import com.algomeet.common.service.AbstractGroupCache;
import com.algomeet.multitenancy.context.TenantContext;
import com.algomeet.xmpp.chatservice.auth.XmppPrincipal;
import com.algomeet.xmpp.chatservice.client.MediaClient;
import com.algomeet.xmpp.chatservice.constant.XmppErrorConditions;
import com.algomeet.xmpp.chatservice.dto.BatchMediaShareRequest;
import com.algomeet.xmpp.chatservice.enums.PresenceType;
import com.algomeet.xmpp.chatservice.enums.XmppErrorType;
import com.algomeet.xmpp.chatservice.enums.XmppMessageType;
import com.algomeet.xmpp.chatservice.properties.DomainProperties;
import com.algomeet.xmpp.chatservice.service.MucMessageReadCursorService;
import com.algomeet.xmpp.chatservice.service.MucMessageService;
import com.algomeet.xmpp.chatservice.service.MucRetractionService;
import com.algomeet.xmpp.chatservice.service.XmppArchiveService;
import com.algomeet.xmpp.chatservice.session.constant.XmppSessionAttributes;
import com.algomeet.xmpp.chatservice.stanza.parser.MediaReferenceParser;
import com.algomeet.xmpp.chatservice.util.JidUtil;
import com.algomeet.xmpp.chatservice.util.SearchUtil;
import com.algomeet.xmpp.chatservice.util.XmppCustomStanzaUtil;
import com.algomeet.xmpp.chatservice.util.XmppReadUtil;
import com.algomeet.xmpp.chatservice.util.XmppServerAckUtil;
import com.algomeet.xmpp.chatservice.util.XmppStanzaUtil;
import com.algomeet.xmpp.chatservice.util.XmppUtil;
import com.github.f4b6a3.uuid.UuidCreator;

import feign.FeignException;
import io.netty.channel.ChannelHandlerContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * <h2>XmppMucHandler</h2>
 * Core coordinator for Multi-User Chat (MUC) logic, routing, and persistence.
 * * <p>This component serves as the central hub for room-based communication. It handles:
 * <ul>
 * <li><b>Authorization:</b> Verifying sender membership and mute status.</li>
 * <li><b>Persistence (MAM):</b> Archiving messages according to XEP-0313.</li>
 * <li><b>Anonymization:</b> Rewriting JIDs to protect user privacy (Occupant JIDs).</li>
 * <li><b>Real-time Routing:</b> Local and Cluster-wide delivery via Netty and Redis.</li>
 * <li><li><b>Push Notifications:</b> Notifying offline or backgrounded users.</li>
 * </ul>
 * * @see <a href="https://xmpp.org/extensions/xep-0045.html">XEP-0045: Multi-User Chat</a>
 * @see <a href="https://xmpp.org/extensions/xep-0313.html">XEP-0313: Message Archive Management</a>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XmppMucHandler {
	private final XmppArchiveService xmppArchiveService;
	private final AbstractGroupCache groupCacheService;
	private final MucAdminCommandRouter mucAdminCommandRouter;
	private final DomainProperties domainProperties;
	private final MucUserCommandRouter mucUserCommandRouter;
	private final MucMessageRouter mucMessageRouter;
	private final JidUtil jidUtil;
	private final XmppReadUtil xmppReadUtil;
	private final XmppUtil xmppUtil;
	private final MucRetractionService mucRetractionService;
	private final MucMessageReadCursorService mucMessageReadService;
	private final MucMessageService mucMessageService;
	private final MediaClient mediaClient;

	// Define a dedicated thread pool for your database work so Netty doesn't starve
	// Pool A: Dedicated ONLY to non-blocking or fast reactive DB tracking/save orchestration
	private static final Scheduler MUC_DB_SCHEDULER = 
	        Schedulers.newBoundedElastic(64, 20000, "xmpp-muc-db");

	// Pool B: Dedicated exclusively to isolating heavy blocking network I/O calls (Feign Clients)
	private static final Scheduler MEDIA_IO_SCHEDULER = 
	        Schedulers.newBoundedElastic(150, 5000, "xmpp-media-io");
	
	/**
	 * Main entry point for MUC stanza processing.
	 * Decides whether a stanza is a moderation command, a user command, or a standard message.
	 * * @param ctx         The Netty channel context for the current TCP connection.
	 * @param id          The 'id' attribute of the XMPP stanza.
	 * @param toRoomJid   The destination JID (e.g., room@conference.domain/<nickname|userkey>).
	 * @param fromJid     The real JID of the sender.
	 * @param type        The message type (e.g., groupchat, error, presence).
	 * @param originalXml The full raw XML payload.
	 * @return A unified reactive stream targeting full execution resolution.
	 */
	public Mono<Void> handleGroupChatRouting(ChannelHandlerContext ctx, String id, String toRoomJid, String fromJid, String type, String originalXml) {
		XmppPrincipal principal = ctx.channel().attr(XmppSessionAttributes.PRINCIPAL).get();
		XmppMessageType msgType = XmppMessageType.fromString(type);

		String toRoomId = XmppUtil.getRoomId(toRoomJid);
		
		// Move all blocking lookup and authorization steps into a deferred reactive container
		return Mono.fromCallable(() -> {
			// Set tenant context safely on the offloaded scheduler thread
			TenantContext.setCurrentTenant(principal.getTenantId());
			try {
				Group group = groupCacheService.getCachedGroup(toRoomId);
				Optional<GroupMember> senderMucMember = SearchUtil.findMember(group, principal.getUserKey());
				return new AuthorizationResult(group, senderMucMember);
			} finally {
				TenantContext.clear(); // Clean up immediately after blocking work finishes
			}
		})
		.subscribeOn(MUC_DB_SCHEDULER) // Shifting the lookup away from Netty Event Loop
		.flatMap((AuthorizationResult auth) -> { // Explicit parameter type hint
			Group group = auth.group;
			Optional<GroupMember> senderMucMember = auth.senderMucMember;

			// Verify if the sender is an authorized member and is not muted
			if((senderMucMember.isEmpty() || senderMucMember.get().isMuted())
					// Ignore unavailable presence stanzas used for member-leave broadcasts
					&& !(XmppStanzaUtil.isPresenceStanza(originalXml) && PresenceType.UNAVAILABLE.getValue().equals(type))) {

				xmppUtil.sendError(ctx, id, fromJid, domainProperties.getGroupChatDomain(), XmppErrorType.CANCEL, 
						XmppErrorConditions.FORBIDDEN, "You are not allowed to send messages to this room");

				log.error("Access Denied: User {} in room {}. (Member: {}, Muted: {})", 
						principal.getUserKey(), toRoomId, senderMucMember.isPresent(), senderMucMember.map(GroupMember::isMuted).orElse(false));
				return Mono.empty(); // Typed hint to align signatures
			}

			if (isModerationCommand(type, originalXml)) {
				// MUC Admin actions (kick, ban, mute)
				return mucAdminCommandRouter.handleCommandStanza(ctx, toRoomJid, originalXml, senderMucMember.get(), principal);
			} else if(isUserCommandStanza(originalXml, toRoomJid)) {
				// MUC User actions (nickname changes, room entry, member-leave broadcasts)
				return mucUserCommandRouter.handleCommandStanza(ctx, type, toRoomJid, originalXml, principal);	
			} else if(XmppStanzaUtil.isRetractStanza(originalXml)) {
				return mucRetractionService.retract(ctx, id, toRoomJid, originalXml, principal);								
			} 

			// 2. DIRECT PRIVATE MESSAGE (PM) WITHIN MUC CHECK
			GroupMember pmToMucMember = resolveDirectPmRecipient(ctx, id, fromJid, toRoomJid, group);

			// 3. ARCHIVING (MAM - XEP-0313)
			// Only archive messages that are storage-eligible (e.g., contain a <body>)
			// Check if it's archivable
			boolean isArchivable = XmppStanzaUtil.isArchivable(originalXml);
			boolean isAckStanza = XmppStanzaUtil.isMessageAckStanza(originalXml);

			/**
			 * Generate a monotonic UUIDv7 used as the stable stanza-id value.
			 * ... (UUIDv7 documentation) ...
			 */
			UUID stanzaId = UuidCreator.getTimeOrderedEpoch();
			
			Mono<Void> processingPipeline = Mono.empty();

			if (isAckStanza) {
				// --- XEP-0333: Chat Markers (Read Receipts) ---
				if (originalXml.contains(XmppReadUtil.NS_DISPLAYS)) {
					String ackMessageId = xmppReadUtil.getAckMessageId(originalXml);
					if (StringUtils.hasText(ackMessageId)) {	
						UUID messageId = UUID.fromString(ackMessageId);
						
						// Chain read updates concurrently safely off the EventLoop
						processingPipeline = Mono.when(
							mucMessageReadService.advanceReadCursor(UUID.fromString(principal.getUserKey()), group.getId(), messageId),
							mucMessageService.bulkMarkRoomMessagesAsRead(messageId)
						).subscribeOn(MUC_DB_SCHEDULER);
					}					
				}
			} else if ((msgType.supportsOfflineStorage() && isArchivable)) {
				// Check for message file attachments
				List<UUID> mediaIds = null;
				try {
					mediaIds = MediaReferenceParser.extractMediaIds(originalXml);

					if (!CollectionUtils.isEmpty(mediaIds)) {
						BatchMediaShareRequest request = new BatchMediaShareRequest();
						request.setMediaIds(mediaIds.stream().map(UUID::toString).collect(Collectors.toSet()));
						request.setGroupId(group.getId());
						request.setMessageId(UUID.fromString(id));			
						
						// FIX: Turn synchronous blocking Feign Client call into a lazy deferred reactive wrapper
						processingPipeline = Mono.fromCallable(() -> shareMedias(senderMucMember.get().getUserKey(), request))
								.subscribeOn(MEDIA_IO_SCHEDULER)
								.flatMap(shared -> {
									if(!shared) {
										xmppUtil.sendError(ctx, id, fromJid, domainProperties.getDomain(), XmppErrorType.CANCEL, 
												XmppErrorConditions.INTERNAL_SERVER_ERROR, "Error sharing media file(s)");
										return Mono.error(new RuntimeException("Media sharing failed"));
									}
									return Mono.empty();
								});
					}				
				} catch(Exception ex) {
					log.error("Error parsing media references {}", originalXml, ex);
					xmppUtil.sendError(ctx, id, fromJid, domainProperties.getDomain(), XmppErrorType.CANCEL, 
							XmppErrorConditions.INTERNAL_SERVER_ERROR, "Error parsing media file(s)");
					return Mono.empty(); // Typed hint to align signatures
				}			
				
				// Insert stanza ID
				String stampedXml = XmppStanzaUtil.insertStanzaId(originalXml, stanzaId.toString(), principal.getDomain());		
				Boolean isCountable = XmppCustomStanzaUtil.isCountableMessage(originalXml);
				
				final List<UUID> finalMediaIds = mediaIds;
				// Chain persistence execution flow sequentially
				processingPipeline = processingPipeline.then(
					xmppArchiveService.archiveEvent(stampedXml, id, XmppUtil.getRoomId(toRoomJid), (pmToMucMember != null ? pmToMucMember.getUserKey() : null), 
							XmppUtil.getUserKey(fromJid), stanzaId, isCountable, finalMediaIds, group.getMessageRetentionDays())
					.flatMap(saved -> {
						// Send an immediate server-level acknowledgment to the sender.
						XmppServerAckUtil.send(ctx, id, domainProperties.getDomain(), stanzaId.toString(), group.getMessageRetentionDays());
						
						Mono<Void> postSaveTasks = Mono.empty();
						
						// Move cursor for the message sender
						if (isCountable) {
							postSaveTasks = postSaveTasks.then(mucMessageReadService.advanceReadCursor(
									UUID.fromString(principal.getUserKey()), group.getId(), UUID.fromString(id)))
									.then();
						}

						log.debug("MAM Archive Success: ID={} Room={}", stanzaId, toRoomId);
						return postSaveTasks;
					})
					.subscribeOn(MUC_DB_SCHEDULER) // Shifting storage execution completely away from Netty Event Loop
					.doOnError(e -> {
						log.error("MAM Archive Failure: {}", e.getMessage(), e);
						handleArchiveError(ctx, id, principal, e);
					})
				);
			}

			// 4. DISPATCHING (Executed safely AFTER database work has settled cleanly)
			final String finalXml = isArchivable ? XmppStanzaUtil.insertStanzaId(originalXml, stanzaId.toString(), principal.getDomain()) : originalXml;
			
			return processingPipeline.then(mucMessageRouter.broadcastToOccupants(ctx, id, toRoomJid, fromJid, msgType, group, pmToMucMember, finalXml))
					.onErrorResume(err -> Mono.empty());
		});
	}
	
	// Simple wrapper class to pass lookup tuple down the flatMap pipeline
	private static class AuthorizationResult {
		final Group group;
		final Optional<GroupMember> senderMucMember;
		AuthorizationResult(Group group, Optional<GroupMember> senderMucMember) {
			this.group = group;
			this.senderMucMember = senderMucMember;
		}
	}
	
	private boolean shareMedias(String fromUserKey, BatchMediaShareRequest request) {
	    int retryCounter = 0;
	    while (++retryCounter <= 3) {
	        try {
	            log.info("Attempt {} to share media batch for user: {}", retryCounter, fromUserKey);
	            mediaClient.batchShare(fromUserKey, request);
	            return true;
	        } catch (FeignException ex) {
	            int status = ex.status();
	            log.error("Feign error sharing media files {}. HTTP Status: {} | Message: {}", 
	                    request.getMediaIds(), status, ex.getMessage());

	            // Fail fast on client errors (4xx) except for specific transient issues like 408 (Timeout) or 429 (Too Many Requests)
	            if (status >= 400 && status < 500 && status != 408 && status != 429) {
	                log.error("Client error encountered ({}). Aborting retries.", status);
	                break;
	            }
	            
	            // For 5xx server errors, 408 timeouts, or 429 rate limits, let the loop continue and retry.
	        } catch (Exception ex) {
	            // Fallback catch for unexpected infrastructure issues (e.g., serialization errors, unknown network issues)
	            log.error("Unexpected error sharing media files {}: {}", request.getMediaIds(), ex.getMessage(), ex);
	        }
	    }
	    
	    return false;
	}

	/**
	 * Resolves a MUC occupant for Private Messaging (PM).
	 * Returns the member if found, or null if the message is a standard group broadcast.
	 * If a nickname was provided but the user isn't found, it handles the error response and throws an exception.
	 */
	private GroupMember resolveDirectPmRecipient(ChannelHandlerContext ctx, String id, String fromJid, String toRoomJid, Group group) {
		String nickname = jidUtil.getNickname(toRoomJid);

		// If no nickname is present, this is a standard group message, not a PM.
		if (!StringUtils.hasText(nickname)) {
			return null;
		}

		return group.getMembers().stream()
				.filter(m -> nickname.equalsIgnoreCase(m.getUserKey()))
				.findFirst()
				.orElseGet(() -> {
					log.error("PM Failure: Nickname {} not found in room {}", nickname, toRoomJid);
					xmppUtil.sendError(ctx, id, fromJid, domainProperties.getGroupChatDomain(), 
							XmppErrorType.CANCEL, XmppErrorConditions.BAD_REQUEST, 
							"Receiver is not member of the group/room.");

					// Throwing a custom exception or a runtime exception stops the execution 
					// of the parent method effectively, replacing the 'return' statement.
					throw new RuntimeException("Receiver not found in room: " + nickname);
				});
	}

	private boolean isModerationCommand(String type, String xml) {
		return XmppMessageType.SET == XmppMessageType.fromString(type) 
				&& xml.contains("http://jabber.org/protocol/muc#admin");
	}

	private void handleArchiveError(ChannelHandlerContext ctx, String id, XmppPrincipal principal, Throwable e) {
		String fromJid = principal.getBareJid();

		if (e instanceof DuplicateKeyException) {
			// Duplicate stanza detected (idempotent case).
			// Client MUST ignore this error; used only to support safe retries.
			xmppUtil.sendError(ctx, id, fromJid, domainProperties.getGroupChatDomain(), XmppErrorType.CANCEL, 
					XmppErrorConditions.DUPLICATE_KEY_ERROR, "Stanza has duplicate key");

		} else {
			xmppUtil.sendError(ctx, id, fromJid, domainProperties.getGroupChatDomain(), XmppErrorType.WAIT, 
					XmppErrorConditions.INTERNAL_SERVER_ERROR, "Storage failure");
		}
	}

	/**
	 * Determines if the incoming XML stanza is a user-initiated command (e.g. Presence Nickname change).
	 *
	 * @param xml     The raw XML payload.
	 * @param roomJid The target JID.
	 * @return {@code true} if targeting a specific occupant resource via presence.
	 */
	private boolean isUserCommandStanza(String xml, String roomJid) {
		if (XmppStanzaUtil.isPresenceStanza(xml)) {
			String[] jidArr = roomJid.split("/");
			return jidArr.length > 1 && StringUtils.hasText(jidArr[1]);
		}
		return false;
	}		
}