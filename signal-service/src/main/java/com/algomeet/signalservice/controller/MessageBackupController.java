package com.algomeet.signalservice.controller;

import static com.algomeet.signalservice.document.MessageBackupDocument.FIELD_DELETED_AT;
import static com.algomeet.signalservice.document.MessageBackupDocument.FIELD_DELIVERED_AT;
import static com.algomeet.signalservice.document.MessageBackupDocument.FIELD_HIDDEN_AT;
import static com.algomeet.signalservice.document.MessageBackupDocument.FIELD_READ_AT;
import static com.algomeet.signalservice.document.MessageBackupDocument.FIELD_SENT_AT;

import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.signalservice.controller.swagger.MessageBackupControllerDoc;
import com.algomeet.signalservice.document.MessageBackupDocument;
import com.algomeet.signalservice.dto.CommonResponse;
import com.algomeet.signalservice.dto.MessageBackupResponse;
import com.algomeet.signalservice.dto.MessageStatusUpdateRequest;
import com.algomeet.signalservice.enums.ResponseCode;
import com.algomeet.signalservice.exceptions.MessageInsertInProgressException;
import com.algomeet.signalservice.exceptions.MessageUpdateStatusInProgressException;
import com.algomeet.signalservice.exceptions.RecordNotFoundException;
import com.algomeet.signalservice.service.MessageBackupService;
import com.algomeet.signalservice.util.SecurityUtil;
import com.github.f4b6a3.uuid.UuidCreator;

import lombok.RequiredArgsConstructor;
/**
 * REST controller that handles CRUD and query operations
 * for backing up and retrieving encrypted chat messages.
 * <p>
 * This controller exposes endpoints to store, fetch, update, and delete
 * message backups, including paginated conversation retrieval between users.
 * </p>
 *
 * @author 
 * @since 1.0
 */
@RestController
@RequestMapping("/signal/backup/chat-messages")
@RequiredArgsConstructor
public class MessageBackupController implements MessageBackupControllerDoc{
	private final MessageBackupService messageBackupService;

	/**
	 * Saves a new chat message backup.
	 *
	 * @param request the message backup data to be stored
	 * @return {@link CommonResponse} with success status
	 */
	@PostMapping
	public ResponseEntity<CommonResponse<?>> saveMessage(@Validated @RequestBody MessageBackupDocument request) { 	
		
		try {
			MessageBackupDocument saved = messageBackupService.insert(request);
			
			if (saved == null) {
				throw new RuntimeException("Error saving the message backup");
			}

			return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, saved));
			
		} catch (MessageInsertInProgressException ex) {
			return ResponseEntity.status(HttpStatus.LOCKED)
					.body(CommonResponse.from(ResponseCode.MESSAGE_SYNC_IN_PROGRESS));           
		} catch (DuplicateKeyException ex) {
			return ResponseEntity.status(HttpStatus.CONFLICT)
					.body(CommonResponse.from(ResponseCode.DUPLICATE_MESSAGE_ID));
		}
	}              

	/**
	 * Retrieves paginated conversation messages between the authenticated user and the given peer.
	 *
	 * Messages are returned in chronological order (oldest → newest).
	 * Supports cursor-based pagination using the optional "after" parameter.
	 *
	 * @param peerKey the unique identifier of the conversation peer (recipient or sender)
	 * @param before optional cursor (stanzaId) to fetch messages that come before a specific message
	 * @param after optional cursor (stanzaId) to fetch messages that come after a specific message
	 * @param page zero-based page index (default: 0)
	 * @param size number of messages per page (default: 50)
	 * @return a paginated list of conversation messages wrapped in a standard response format
	 */
	@GetMapping("/{peerKey}/conversation")
	public ResponseEntity<CommonResponse<List<MessageBackupResponse>>> getConversationMessages(
	        @PathVariable UUID peerKey,
	        @RequestParam(value = "before", required = false) UUID beforeStanzaId,
	        @RequestParam(value = "after", required = false) UUID afterStanzaId,
	        @RequestParam(defaultValue = "0") int page,
	        @RequestParam(defaultValue = "50") int size) {

	    UUID userKey = UUID.fromString(SecurityUtil.getUserKey());
	    List<MessageBackupDocument> messages;

	    if (afterStanzaId != null) {			
	        messages = messageBackupService.getConversationMessagesAfter(
	                userKey, peerKey, afterStanzaId, page, size);    		
	    } else {    
	        // Fallback to a new time-ordered UUID if 'before' is completely absent
	        UUID targetBeforeId = (beforeStanzaId != null) ? beforeStanzaId : UuidCreator.getTimeOrderedEpoch();
	        
	        messages = messageBackupService.getConversationMessagesBefore(
	                userKey, peerKey, targetBeforeId, page, size);
	        
	        // Sort ascending by stanzaId
	        messages.sort(Comparator.comparing(MessageBackupDocument::getStanzaId));
	    }

	    // Transform and map to response DTOs
	    List<MessageBackupResponse> responseList = messages.stream()
	            .map(MessageBackupResponse::from)
	            .toList(); // Modern Java 16+ syntax

	    return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, responseList));
	}
	
	@GetMapping("/{peerKey}/conversation/updates")
	public ResponseEntity<CommonResponse<List<MessageBackupResponse>>> getMessageUpdates(
			@PathVariable UUID peerKey,
			@RequestParam("untilStanzaId") UUID untilStanzaId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {

		List<MessageBackupDocument> backupsPage =
				messageBackupService.getMessageUpdates(
						UUID.fromString(SecurityUtil.getUserKey()), 
						peerKey, 
						untilStanzaId, 
						untilStanzaId, 
						page,
						size);

		List<MessageBackupResponse> responseList = backupsPage
				.stream() 
				.map(MessageBackupResponse::from)
				.collect(Collectors.toList());
		
		// Sort in ascending order
		responseList.sort(Comparator.comparing(MessageBackupResponse::getStanzaId));
		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, responseList));
	}
	
	@Deprecated
	@GetMapping("/{peerKey}/conversation/sync")
	public ResponseEntity<CommonResponse<List<MessageBackupResponse>>> syncMessages(
			@PathVariable UUID peerKey,
			@RequestParam("before") UUID before,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "1000") int size) {

		List<MessageBackupDocument> backupsPage =
				messageBackupService.getMessageUpdates(UUID.fromString(SecurityUtil.getUserKey()), 
						peerKey, 
						before, 
						before, 
						page,
						size);

		List<MessageBackupResponse> responseList = backupsPage
				.stream() 
				.map(MessageBackupResponse::from)
				.collect(Collectors.toList());

		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, responseList));
	}

	/**
	 * Retrieves a list of message backups by their message IDs.
	 *
	 * @param messageIds the list of message IDs to fetch
	 * @return list of {@link MessageBackupResponse} wrapped in a {@link CommonResponse}
	 */
	@GetMapping
	public ResponseEntity<CommonResponse<List<MessageBackupResponse>>> getMessages(@RequestParam List<UUID> messageIds) {
		List<MessageBackupDocument> messageList = messageBackupService.getMessages(messageIds);     
		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, 
				messageList.stream().map(mb -> MessageBackupResponse.from(mb)).toList()));
	}
	
	@Deprecated
	@GetMapping("/contacts")
	public ResponseEntity<CommonResponse<List<String>>> getConversationContacts() {

		// Retrieve the currently authenticated user's unique identifier
		String currentUserKey = SecurityUtil.getUserKey();

		// Delegate to service layer to fetch distinct chat partners (peer user keys)
		List<String> contacts = messageBackupService.getConversationContacts(
				UUID.fromString(currentUserKey));

		// Wrap result in a standardized API response structure and return HTTP 200
		return ResponseEntity.ok(
				CommonResponse.from(ResponseCode.SUCCESS, contacts)
				);
	}
	
	/**
     * Retrieves the latest message backup document for each unique conversation 
     * belonging to the authenticated user. 
     * <p>
     * This acts as the user's archival inbox overview, providing a snapshot 
     * of the most recent interaction for all active chat threads.
     *
     * @return A standard API wrapper containing a list of the latest {@link MessageBackupDocument}s
     */
    @GetMapping("/conversations")
    public ResponseEntity<CommonResponse<List<MessageBackupDocument>>> getConversations() {

        // 1. Resolve the principal identity of the currently authenticated session
        String currentUserKey = SecurityUtil.getUserKey();

        // 2. Query the backup database for distinct conversation tracks, 
        // passing the parsed UUID to pull the latest chronological record for each chat
        List<MessageBackupDocument> messages = messageBackupService.findUniqueConversationsWithFullDetails(
                UUID.fromString(currentUserKey));

        // 3. Encapsulate the result matrix within a uniform response structure and return an HTTP 200 OK
        return ResponseEntity.ok(
                CommonResponse.from(ResponseCode.SUCCESS, messages)
        );
    }

	/**
	 * Retrieves a single message backup by its message ID.
	 *
	 * @param messageId the ID of the message to fetch
	 * @return {@link MessageBackupResponse} if found, otherwise HTTP 404
	 */
	@GetMapping("/{messageId}")
	public ResponseEntity<CommonResponse<MessageBackupResponse>> getMessage(@PathVariable UUID messageId) {
		try {
			MessageBackupDocument saved = messageBackupService.getMessage(UUID.fromString(SecurityUtil.getUserKey()), messageId);     
			return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, MessageBackupResponse.from(saved)));

		} catch (RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponse.from(ResponseCode.MESSAGE_BACKUP_NOT_FOUND));
		}
	}

	/**
	 * Updates an existing message backup by its message ID.
	 *
	 * @param messageId the ID of the message to update
	 * @param request   the updated message content
	 * @return updated {@link MessageBackupResponse} if found, otherwise HTTP 404
	 */
	@PutMapping("/{messageId}")
	public ResponseEntity<CommonResponse<MessageBackupResponse>> updateMessage(@PathVariable UUID messageId, 
			@RequestBody MessageBackupDocument request) {
		try {
			MessageBackupDocument saved = messageBackupService.update(UUID.fromString(SecurityUtil.getUserKey()), messageId, request);
			return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, MessageBackupResponse.from(saved)));
		} catch (RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponse.from(ResponseCode.MESSAGE_BACKUP_NOT_FOUND));
		}
	}

	
	/**
	 * Deletes all messages in a conversation between the authenticated user and the given peer.
	 *
	 * @param peerKey the unique identifier of the peer user
	 * @return success response
	 */
	@DeleteMapping("/{peerKey}/conversation")
	public ResponseEntity<CommonResponse<?>> deleteByConversation(@PathVariable UUID peerKey,
			@RequestParam(name = "lastStanzaId", required = false) UUID lastStanzaId) {
		
		messageBackupService.deleteConversation(UUID.fromString(SecurityUtil.getUserKey()), peerKey, lastStanzaId);          
		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
	}

		
	/**
     * Deletes all chat message backups belonging to the currently authenticated user.
     *
     * @return success response
     */
    @DeleteMapping("/purge")
	public ResponseEntity<CommonResponse<?>> deleteByUserKey() {
		messageBackupService.deleteByUserKey(UUID.fromString(SecurityUtil.getUserKey()));        
		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
	}

	/**
	 * Marks a message as successfully sent.
	 */
	@PatchMapping("/mark-as-sent")
	public ResponseEntity<CommonResponse<?>> markAsSent(
			@Validated @RequestBody MessageStatusUpdateRequest request) {
		return processStatusUpdate(FIELD_SENT_AT, request);
	}

	/**
	 * Marks a message as delivered.
	 */
	@PatchMapping("/mark-as-delivered")
	public ResponseEntity<CommonResponse<?>> markAsDelivered(
			@Validated @RequestBody MessageStatusUpdateRequest request) {
		return processStatusUpdate(FIELD_DELIVERED_AT, request);
	}

	/**
	 * Marks a message as read.
	 */
	@PatchMapping("/{messageId}/mark-as-read")
	public ResponseEntity<CommonResponse<?>> markAsRead(
	        @PathVariable UUID messageId,
	        @RequestParam(value = "date", required = false) Long date) {		
	    
	    MessageStatusUpdateRequest request = new MessageStatusUpdateRequest();
	    request.setMessageIds(List.of(messageId));
	    
	    // Use Optional.ofNullable to handle the nullability cleanly
	    Optional.ofNullable(date).ifPresent(request::setDate);

	    return processStatusUpdate(FIELD_READ_AT, request);
	}
	
	/**
	 * Performs a soft delete (tombstone) on a message.
	 */
	@PatchMapping("/mark-as-retracted")
	public ResponseEntity<CommonResponse<?>> markAsRetracted(
			@Validated @RequestBody MessageStatusUpdateRequest request) {
		return processStatusUpdate(FIELD_DELETED_AT, request);
	}
	
	/**
	 * Performs a soft delete on a message.
	 */
	@PatchMapping("/mark-as-hidden")
	public ResponseEntity<CommonResponse<?>> markAsHidden(
			@Validated @RequestBody MessageStatusUpdateRequest request) {
		return processStatusUpdate(FIELD_HIDDEN_AT, request);
	}

	/**
	 * Private helper to DRY up the status update logic and handle parsing.
	 */
	private ResponseEntity<CommonResponse<?>> processStatusUpdate(
			String fieldName, 
			MessageStatusUpdateRequest request) {

		try {
			long timestamp;

			// Fallback logic: Use client date if present, otherwise use server's current time
			if (request.getDate() != null) {
				timestamp = request.getDate();
			} else {
				timestamp = System.currentTimeMillis();
			}

			messageBackupService.updateStatus(
					request.getMessageIds(), 
					fieldName, 
					timestamp);

			return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
		} catch (MessageUpdateStatusInProgressException ex) {
			return ResponseEntity.status(HttpStatus.LOCKED)
					.body(CommonResponse.from(ResponseCode.MESSAGE_SYNC_IN_PROGRESS));           
		} catch (DateTimeParseException ex) {
			// Only hits if date was provided but was malformed
			return ResponseEntity.badRequest()
					.body(CommonResponse.from(ResponseCode.INVALID_DATE_FORMAT));
		} catch (RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(CommonResponse.from(ResponseCode.MESSAGE_BACKUP_NOT_FOUND));
		}
	}
	
	/**
     * Retrieves the metadata or payload of the last message sent by the current authenticated user 
     * within a specific conversation.
     *
     * @param peerKey The unique identifier of the other chat participant.
     * @return A standard API response containing the last outbound message response DTO, 
     * or a successful payload with null data if no message has been sent yet.
     */
    @GetMapping("/{peerKey}/conversation/last-sent")
    public ResponseEntity<CommonResponse<MessageBackupResponse>> getConversationLastSent(
            @PathVariable UUID peerKey) {

        // Extract the authenticated user's unique identifier from the security context
        UUID userKey = UUID.fromString(SecurityUtil.getUserKey());
            
        // Fetch the last sent message. Passing 'userKey' as the third argument 
        // filters the repository query down to messages where senderKey == userKey.
        MessageBackupResponse responseBody = messageBackupService.getConversationLastSent(userKey, peerKey, userKey)
                .map(MessageBackupResponse::from)
                .orElse(null); 

        return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, responseBody));
    }	
    
    /**
     * Retrieves the metadata or payload of the last message received by the current authenticated user 
     * (sent by the peer) within a specific conversation.
     *
     * @param peerKey The unique identifier of the chat participant who authored the message.
     * @return A standard API response containing the last inbound message response DTO, 
     * or a successful payload with null data if no message has been received yet.
     */
    @GetMapping("/{peerKey}/conversation/last-received")
    public ResponseEntity<CommonResponse<MessageBackupResponse>> getConversationLastReceived(
            @PathVariable UUID peerKey) {

        // Extract the authenticated user's unique identifier from the security context
        UUID userKey = UUID.fromString(SecurityUtil.getUserKey());
            
        // Fetch the last received message. Passing 'peerKey' as the third argument 
        // filters the repository query down to messages where senderKey == peerKey.
        MessageBackupResponse responseBody = messageBackupService.getConversationLastSent(userKey, peerKey, peerKey)
                .map(MessageBackupResponse::from)
                .orElse(null); 

        return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, responseBody));
    }
}
