package com.algomeet.xmpp.chatservice.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.xmpp.chatservice.controller.doc.MucMessageControllerDoc;
import com.algomeet.xmpp.chatservice.dto.CommonResponse;
import com.algomeet.xmpp.chatservice.dto.MucMessageResponse;
import com.algomeet.xmpp.chatservice.enums.ResponseCode;
import com.algomeet.xmpp.chatservice.exceptions.GroupNotFoundException;
import com.algomeet.xmpp.chatservice.service.MucMessageService;
import com.algomeet.xmpp.chatservice.service.MucRoomService;
import com.algomeet.xmpp.chatservice.util.SecurityUtil;
import com.github.f4b6a3.uuid.UuidCreator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat/muc")
public class MucMessageController implements MucMessageControllerDoc{
	private final MucMessageService mucMessageService;
	private final MucRoomService mucRoomService;

	/**
	 * Retrieves paginated messages for a specific MUC (group chat) conversation.
	 * <p>
	 * Supports cursor-based pagination using UUID v7 stanza IDs:
	 * <ul>
	 *     <li><b>after</b> → fetch newer messages after the provided stanza ID</li>
	 *     <li><b>before</b> → fetch older messages before the provided stanza ID</li>
	 * </ul>
	 * <p>
	 * This endpoint is commonly used for:
	 * <ul>
	 *     <li>initial conversation loading</li>
	 *     <li>infinite scroll history loading</li>
	 *     <li>incremental message synchronization</li>
	 *     <li>backup restoration</li>
	 * </ul>
	 *
	 * @param groupId The unique MUC room identifier.
	 * @param beforeStanzaId Cursor used to retrieve older messages.
	 * @param afterStanzaId Cursor used to retrieve newer messages.
	 * @param page Pagination page index.
	 * @param size Number of messages per page.
	 * @return A standardized {@link CommonResponse} containing a list of
	 *         {@link MucMessageResponse} objects ordered chronologically.
	 */
	@GetMapping("/{groupId}/messages")
	public ResponseEntity<CommonResponse<List<MucMessageResponse>>> getMessages(
	        @PathVariable UUID groupId,
	        @RequestParam(value = "before", required = false) UUID beforeStanzaId,
	        @RequestParam(value = "after", required = false) UUID afterStanzaId,    		
	        @RequestParam(value = "page", defaultValue = "0") int page, 
	        @RequestParam(value = "size", defaultValue = "20") int size) {

	    UUID userKey = UUID.fromString(SecurityUtil.getUserKey());
	    List<MucMessageResponse> messages;

	    if (afterStanzaId != null) {
	        messages = mucMessageService.getMessagesAfter(
	                userKey, groupId, afterStanzaId, page, size);   
	    } else {
	        // Fallback: If 'before' is missing, default to a fresh time-ordered UUID
	        UUID targetBeforeId = (beforeStanzaId != null) ? beforeStanzaId : UuidCreator.getTimeOrderedEpoch();
	        
	        messages = mucMessageService.getMessagesBefore(
	                userKey, groupId, targetBeforeId, page, size);   
	    }

	    return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, messages));
	}

	/**
	 * Retrieves incremental message updates for a MUC (group chat) conversation.
	 * <p>
	 * Returns message state changes up to the provided stanza ID including:
	 * <ul>
	 *     <li>message edits</li>
	 *     <li>message deletions/retractions</li>
	 *     <li>read state updates</li>
	 *     <li>delivery state updates</li>
	 *     <li>reaction updates</li>
	 * </ul>
	 * <p>
	 * This endpoint is intended for conversations that have already been
	 * partially synchronized locally.
	 *
	 * @param groupId The unique MUC room identifier.
	 * @param untilStanzaId Upper synchronization boundary stanza ID.
	 * @param page Pagination page index.
	 * @param size Number of update records per page.
	 * @return A standardized {@link CommonResponse} containing incremental
	 *         {@link MucMessageResponse} update records.
	 */
	@GetMapping("/{groupId}/massages/updates")
	public ResponseEntity<CommonResponse<List<MucMessageResponse>>> getMessageUpdates(
			@PathVariable UUID groupId,
			@RequestParam("untilStanzaId") UUID untilStanzaId,
			@RequestParam(value = "page", defaultValue = "0") int page,
			@RequestParam(value = "size", defaultValue = "20") int size) {

		// Get the authenticated user's key
		String userKey = SecurityUtil.getUserKey();                  

		return ResponseEntity.ok(CommonResponse.from(
				ResponseCode.SUCCESS, 
				mucMessageService.getMessageUpdates(UUID.fromString(userKey), groupId, untilStanzaId, page, size)
				));
	}  

	/**
     * Retrieves the chat inbox overview for the currently authenticated user.
     * <p>
     * This endpoint compiles a real-time list of all conversational channels 
     * (MUC Rooms) the user belongs to, populated exclusively with the absolute 
     * latest visible message snippet from each thread. It automatically respects 
     * privacy conditions (hidden messages) and message isolation rules (private whispers).
     * <p>
     * Typically utilized by UI layers to render the master-detail sidebar layout 
     * or active chat thread selector immediately upon client connection.
     *
     * @return A standardized {@link CommonResponse} wrapper enclosing a list of 
     *         {@link MucMessageResponse} objects sorted by recent activity.
     */
	@GetMapping("/conversations")
	public ResponseEntity<CommonResponse<List<MucMessageResponse>>> getConversations(){
		// Get the authenticated user's key
		String userKey = SecurityUtil.getUserKey();                  

		return ResponseEntity.ok(CommonResponse.from(
				ResponseCode.SUCCESS, 
				mucMessageService.getConversations(UUID.fromString(userKey))
				));
	}
	
	
	/**
	 * Clears the calling user's personal view of the group chat conversation history timeline.
	 * <p>
	 * This self-serve endpoint captures the authenticated user's key from the security context
	 * and registers the current server system time as their historical message visibility threshold.
	 * Messages generated prior to this point are filtered out during subsequent sync or fetch operations.
	 * </p>
	 *
	 * @param groupId the unique identifier of the target group chat
	 * @return a response wrapper containing {@code true} if the database cutoff record was updated successfully
	 */
	@PostMapping("/{groupId}/timeline-cutoff")
	public ResponseEntity<CommonResponse<Boolean>> clearMemberHistoryTimeline(
			@PathVariable UUID groupId) {

		UUID userKey = UUID.fromString(SecurityUtil.getUserKey());        

		boolean cleared = mucRoomService.clearMemberHistoryTimeline(
				groupId,
				userKey,
				Instant.now()).block();
				
		return ResponseEntity.ok(
				CommonResponse.from(ResponseCode.SUCCESS, cleared));
	}
	
	@DeleteMapping("/{groupId}/messages")
    public ResponseEntity<CommonResponse<Boolean>> purgeGroupMessages(
            @PathVariable UUID groupId) {
		UUID userKey = UUID.fromString(SecurityUtil.getUserKey());  
		
        log.warn("Administrative trigger: Hard purging all message records for group {}", groupId);
        try {
        boolean purged = mucRoomService.purgeGroupConversation(groupId, userKey);
                
        return ResponseEntity.ok(
                CommonResponse.from(ResponseCode.SUCCESS, purged));
        } catch (AccessDeniedException ex) {
        	return ResponseEntity.status(HttpStatus.FORBIDDEN)     
        			.body(CommonResponse.from(ResponseCode.ERROR, false));
        }
    }
	
	@PostMapping("/{groupId}/apply-message-retention-policy")
	public Mono<ResponseEntity<CommonResponse<Object>>> applyMessageRetentionPolicy(
	        @PathVariable UUID groupId,
	        @RequestParam Integer messageRetentionDays) {
	    
	    // Assuming you have a way to extract the current user's UUID (e.g., from a security context or session)
	    // Replace 'currentUserKey' with your actual user context extraction logic.
	    UUID currentUserKey = UUID.fromString(SecurityUtil.getUserKey());  

	    Integer retentionDays = messageRetentionDays != -1 ? messageRetentionDays : null;
	    return mucRoomService.applyMessageRetentionPolicy(currentUserKey, groupId, retentionDays)
	            // .then() waits for completion (empty or not) and switches to your success response
	            .then(Mono.just(ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS))))
	            .onErrorReturn(
	                    AccessDeniedException.class, 
	                    ResponseEntity.status(HttpStatus.FORBIDDEN).body(CommonResponse.from(ResponseCode.ERROR))
	            )
	            .onErrorReturn(
	                    GroupNotFoundException.class, 
	                    ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponse.from(ResponseCode.ERROR))
	            )
	            .onErrorReturn(
	            		IllegalStateException.class, 
	                    ResponseEntity.status(HttpStatus.CONFLICT).body(CommonResponse.from(ResponseCode.MESSAGE_RETENTION_UPDATE_IN_PROGRESS))
	            )	            
	            .onErrorResume(Exception.class, Mono::error);
	}
}