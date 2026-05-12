package com.algomeet.signalservice.controller;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
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
import com.github.f4b6a3.ulid.UlidCreator;
import static com.algomeet.signalservice.document.MessageBackupDocument.*;

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
		MessageBackupDocument saved = messageBackupService.insert(request);
		try {
			if (saved == null) {
				throw new RuntimeException("Error saving the message backup");
			}

			return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, saved));
			
		} catch (MessageInsertInProgressException ex) {
			return ResponseEntity.status(HttpStatus.LOCKED)
					.body(CommonResponse.from(ResponseCode.MESSAGE_SYNC_IN_PROGRESS));           
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
	public ResponseEntity<CommonResponse<Page<MessageBackupResponse>>> getConversationMessages(
			@PathVariable String peerKey,
			@RequestParam("before") Optional<String> before,
			@RequestParam("after") Optional<String> after,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size) {

		// Fetch paginated message backups for the conversation between the current user and the peer.
		// NOTE: The "after" cursor is not yet applied in the query and should be integrated at the service level.
		Page<MessageBackupDocument> backupsPage = null;
		if (after.isPresent() && StringUtils.hasText(after.get())) {
			backupsPage =
					messageBackupService.getConversationMessagesAfter(
							SecurityUtil.getUserKey(), peerKey, after.get(), page, size);    		

		} else {    
			if (before.isEmpty()) {
				before = Optional.ofNullable(UlidCreator.getMonotonicUlid().toLowerCase());
			}
			
			backupsPage =
					messageBackupService.getConversationMessagesBefore(
							SecurityUtil.getUserKey(), peerKey, before.get(), page, size);
		}

		// Transform database documents into API response DTOs.
		List<MessageBackupResponse> responseList = backupsPage.getContent()
				.stream()
				.map(MessageBackupResponse::from)
				.collect(Collectors.toList());

		// Wrap transformed results into a new Page object while preserving pagination metadata.
		Page<MessageBackupResponse> responsePage =
				new PageImpl<>(responseList, PageRequest.of(page, size), backupsPage.getTotalElements());

		// Return standardized success response with paginated data.
		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, responsePage));
	}     

	@GetMapping("/{peerKey}/conversation/sync")
	public ResponseEntity<CommonResponse<List<MessageBackupResponse>>> syncMessages(
			@PathVariable String peerKey,
			@RequestParam("before") String before,
			@RequestParam(defaultValue = "5000") int maxResults) {

		List<MessageBackupDocument> backupsPage =
				messageBackupService.syncMessageUpdates(SecurityUtil.getUserKey(), peerKey, before, before, maxResults);

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
	public ResponseEntity<CommonResponse<List<MessageBackupResponse>>> getMessages(@RequestParam List<String> messageIds) {
		List<MessageBackupDocument> messageList = messageBackupService.getMessages(messageIds);     
		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, 
				messageList.stream().map(mb -> MessageBackupResponse.from(mb)).toList()));
	}

	/**
	 * Retrieves a list of user keys representing all distinct chat partners
	 * (1:1 conversation contacts) for the currently authenticated user.
	 *
	 * <p>This endpoint:
	 * <ul>
	 *   <li>Identifies the current user via {@link SecurityUtil#getUserKey()}</li>
	 *   <li>Fetches unique conversation IDs associated with the user</li>
	 *   <li>Extracts the "peer" user key from each conversation</li>
	 *   <li>Returns a distinct list of user keys the user has interacted with</li>
	 * </ul>
	 *
	 * <p>Use case:
	 * <ul>
	 *   <li>Populate recent chats / contact list</li>
	 *   <li>Display users with whom the current user had direct conversations</li>
	 * </ul>
	 *
	 * @return ResponseEntity containing:
	 *         <ul>
	 *           <li>status: {@link ResponseCode#SUCCESS}</li>
	 *           <li>data: List of peer user keys</li>
	 *         </ul>
	 */
	@GetMapping("/contacts")
	public ResponseEntity<CommonResponse<List<String>>> getConversationContacts() {

		// Retrieve the currently authenticated user's unique identifier
		String currentUserKey = SecurityUtil.getUserKey();

		// Delegate to service layer to fetch distinct chat partners (peer user keys)
		List<String> contacts = messageBackupService.getConversationContacts(currentUserKey);

		// Wrap result in a standardized API response structure and return HTTP 200
		return ResponseEntity.ok(
				CommonResponse.from(ResponseCode.SUCCESS, contacts)
				);
	}

	/**
	 * Retrieves a single message backup by its message ID.
	 *
	 * @param messageId the ID of the message to fetch
	 * @return {@link MessageBackupResponse} if found, otherwise HTTP 404
	 */
	@GetMapping("/{messageId}")
	public ResponseEntity<CommonResponse<MessageBackupResponse>> getMessage(@PathVariable String messageId) {
		try {
			MessageBackupDocument saved = messageBackupService.getMessage(SecurityUtil.getUserKey(), messageId);     
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
	public ResponseEntity<CommonResponse<MessageBackupResponse>> updateMessage(@PathVariable String messageId, 
			@RequestBody MessageBackupDocument request) {
		try {
			MessageBackupDocument saved = messageBackupService.update(SecurityUtil.getUserKey(), messageId, request);
			return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, MessageBackupResponse.from(saved)));
		} catch (RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponse.from(ResponseCode.MESSAGE_BACKUP_NOT_FOUND));
		}
	}

	/**
	 * Updates (edits) an existing message in the conversation.
	 *
	 * This endpoint allows modification of a previously sent message.
	 * Typically used for message correction or updates after initial delivery.
	 *
	 * @param messageId the unique identifier of the message to be edited
	 * @param request the request payload containing updated message fields
	 * @return the updated message wrapped in a standard response format
	 *
	 * @throws RecordNotFoundException if the message with the given ID does not exist
	 */
	@PutMapping("/{messageId}/edit")
	public ResponseEntity<CommonResponse<MessageBackupResponse>> editMessage(@PathVariable String messageId, 
			@RequestBody MessageBackupDocument request) {
		try {
			MessageBackupDocument saved = messageBackupService.edit(SecurityUtil.getUserKey(), messageId, request);
			return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, MessageBackupResponse.from(saved)));
		} catch (RecordNotFoundException ex) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponse.from(ResponseCode.MESSAGE_BACKUP_NOT_FOUND));
		}
	}

	/**
	 * Deletes a single message backup by its message ID.
	 *
	 * @param messageId the ID of the message to delete
	 * @return success response or HTTP 404 if not found
	 */
	@DeleteMapping("/{messageId}")
	public ResponseEntity<CommonResponse<?>> deleteMessage(@PathVariable String messageId) {
		try {
			messageBackupService.delete(SecurityUtil.getUserKey(), messageId);        
			return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
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
	public ResponseEntity<CommonResponse<?>> deleteByConversation(@PathVariable String peerKey) {
		messageBackupService.deleteConversation(SecurityUtil.getUserKey(), peerKey);          
		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
	}

	/**
	 * Deletes all chat message backups belonging to the currently authenticated user.
	 *
	 * @return success response
	 */
	@DeleteMapping
	public ResponseEntity<CommonResponse<?>> deleteByUserKey() {
		messageBackupService.deleteByUserKey(SecurityUtil.getUserKey());        
		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
	}

	/**
	 * Marks a message as successfully sent.
	 */
	@PatchMapping("/{messageId}/mark-as-sent")
	public ResponseEntity<CommonResponse<?>> markAsSent(
			@PathVariable String messageId,
			@Validated @RequestBody MessageStatusUpdateRequest request) {
		return processStatusUpdate(messageId, FIELD_SENT_AT, request);
	}

	/**
	 * Marks a message as delivered.
	 */
	@PatchMapping("/{messageId}/mark-as-delivered")
	public ResponseEntity<CommonResponse<?>> markAsDelivered(
			@PathVariable String messageId,
			@Validated @RequestBody MessageStatusUpdateRequest request) {
		return processStatusUpdate(messageId, FIELD_DELIVERED_AT, request);
	}

	/**
	 * Marks a message as read.
	 */
	@PatchMapping("/{messageId}/mark-as-read")
	public ResponseEntity<CommonResponse<?>> markAsRead(
			@PathVariable String messageId,
			@Validated @RequestBody MessageStatusUpdateRequest request) {
		return processStatusUpdate(messageId, FIELD_READ_AT, request);
	}

	/**
	 * Performs a soft delete (tombstone) on a message.
	 */
	@PatchMapping("/{messageId}/mark-as-deleted")
	public ResponseEntity<CommonResponse<?>> markAsDeleted(
			@PathVariable String messageId,
			@Validated @RequestBody MessageStatusUpdateRequest request) {
		return processStatusUpdate(messageId, FIELD_DELETED_AT, request);
	}
	
	/**
	 * Performs a soft delete (tombstone) on a message.
	 */
	@PatchMapping("/{messageId}/mark-as-retracted")
	public ResponseEntity<CommonResponse<?>> markAsRetracted(
			@PathVariable String messageId,
			@Validated @RequestBody MessageStatusUpdateRequest request) {
		return processStatusUpdate(messageId, FIELD_RETRACTED_AT, request);
	}

	/**
	 * Private helper to DRY up the status update logic and handle parsing.
	 */
	private ResponseEntity<CommonResponse<?>> processStatusUpdate(
			String messageId, 
			String fieldName, 
			MessageStatusUpdateRequest request) {

		try {
			long timestamp;

			// Fallback logic: Use client date if present, otherwise use server's current time
			if (request.getDate() != null && !request.getDate().isBlank()) {
				timestamp = Instant.parse(request.getDate()).toEpochMilli();
			} else {
				timestamp = System.currentTimeMillis();
			}

			String stanzaId;
			if (StringUtils.hasText(request.getStanzaId())) {
				stanzaId = request.getStanzaId();            	
			} else {
				stanzaId = UlidCreator.getMonotonicUlid().toLowerCase();
			}

			messageBackupService.updateStatus(
					messageId, 
					fieldName, 
					stanzaId, 
					timestamp
					);

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
}
