package com.algomeet.xmpp.chatservice.controller;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.xmpp.chatservice.controller.doc.MucMessageControllerDoc;
import com.algomeet.xmpp.chatservice.dto.CommonResponse;
import com.algomeet.xmpp.chatservice.dto.MucMessageResponse;
import com.algomeet.xmpp.chatservice.enums.ResponseCode;
import com.algomeet.xmpp.chatservice.service.MucMessageService;
import com.algomeet.xmpp.chatservice.util.SecurityUtil;

import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat/muc")
public class MucMessageController implements MucMessageControllerDoc{
	private final MucMessageService mucMessageService;

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
			@RequestParam("before") Optional<String> beforeStanzaId,
			@RequestParam("after") Optional<String> afterStanzaId,    		
			@RequestParam(value = "page", defaultValue = "0") int page, 
			@RequestParam(value = "size", defaultValue = "20") int size) {

		// Get the authenticated user's key
		String userKey = SecurityUtil.getUserKey();

		List<MucMessageResponse> messages = null;
		if (afterStanzaId.isPresent()) {
			messages = mucMessageService.getMessagesAfter(UUID.fromString(userKey), 
					groupId, 
					UUID.fromString(afterStanzaId.get()), 
					page, size);   
		} else if (beforeStanzaId.isPresent()){
			messages = mucMessageService.getMessagesBefore(UUID.fromString(userKey), 
					groupId, 
					UUID.fromString(beforeStanzaId.get()), 
					page, size);   
		}

		return ResponseEntity.ok(CommonResponse.from(
				ResponseCode.SUCCESS, 
				messages
				));
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
			@Parameter(description = "Page index", example = "0") int page,
			@Parameter(description = "Page size", example = "50") int size) {

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
}