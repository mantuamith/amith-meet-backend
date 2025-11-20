package com.algomeet.signalingservice.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.signalingservice.controller.swagger.ChatMessageBackupControllerDoc;
import com.algomeet.signalingservice.document.MessageBackupDocument;
import com.algomeet.signalingservice.dto.CommonResponse;
import com.algomeet.signalingservice.dto.MessageBackupResponse;
import com.algomeet.signalingservice.enums.ResponseCode;
import com.algomeet.signalingservice.exceptions.RecordNotFoundException;
import com.algomeet.signalingservice.service.MessageBackupService;
import com.algomeet.signalingservice.util.SecurityUtil;

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
@RequestMapping("/signaling/backup/chat-messages")
@RequiredArgsConstructor
public class ChatMessageBackupController implements ChatMessageBackupControllerDoc{
	private final MessageBackupService messageBackupService;

	/**
     * Saves a new chat message backup.
     *
     * @param request the message backup data to be stored
     * @return {@link CommonResponse} with success status
     */
    @PostMapping
    public ResponseEntity<CommonResponse<?>> saveMessage(@RequestBody MessageBackupDocument request) {
    	request.setUserKey(SecurityUtil.getUserKey());
    	
    	MessageBackupDocument saved = messageBackupService.insert(request);
    	if (saved == null) {
    		throw new RuntimeException("Error saving the message backup");
    	}
    	
        return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
    }
    
    /**
     * Retrieves a paginated list of chat messages exchanged between the
     * currently authenticated user and the specified peer.
     *
     * @param peerKey the unique identifier of the peer user
     * @param page    the page number (default = 0)
     * @param size    the page size (default = 50)
     * @return {@link Page} of {@link MessageBackupResponse} objects wrapped in a {@link CommonResponse}
     */
    @GetMapping("/{peerKey}/conversation")
    public ResponseEntity<CommonResponse<Page<MessageBackupResponse>>> getMessagesConversations(
            @PathVariable String peerKey,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Page<MessageBackupDocument> backupsPage =
                messageBackupService.getConversation(SecurityUtil.getUserKey(), peerKey, page, size);

        List<MessageBackupResponse> responseList = backupsPage.getContent()
                .stream() 
                .map(MessageBackupResponse::from)
                .collect(Collectors.toList());

        Page<MessageBackupResponse> responsePage =
                new PageImpl<>(responseList, PageRequest.of(page, size), backupsPage.getTotalElements());
        return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, responsePage));
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
     * Retrieves a single message backup by its message ID.
     *
     * @param messageId the ID of the message to fetch
     * @return {@link MessageBackupResponse} if found, otherwise HTTP 404
     */
    @GetMapping("/{messageId}")
    public ResponseEntity<CommonResponse<MessageBackupResponse>> getMessage(@PathVariable String messageId) {
    	try {
    		MessageBackupDocument saved = messageBackupService.getMessage(messageId);     
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
    		MessageBackupDocument saved = messageBackupService.update(messageId, request);
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
    		messageBackupService.delete(messageId);        
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
}
