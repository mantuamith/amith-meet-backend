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

import com.algomeet.signalingservice.document.MessageBackupDocument;
import com.algomeet.signalingservice.dto.CommonResponse;
import com.algomeet.signalingservice.dto.MessageBackupResponse;
import com.algomeet.signalingservice.enums.ResponseCode;
import com.algomeet.signalingservice.exceptions.RecordNotFoundException;
import com.algomeet.signalingservice.service.MessageBackupService;
import com.algomeet.signalingservice.util.SecurityUtil;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/signaling/backup/chat-messages")
@RequiredArgsConstructor
public class ChatMessageBackupController {
	private final MessageBackupService messageBackupService;

    @PostMapping
    public ResponseEntity<CommonResponse<?>> saveMessage(@RequestBody MessageBackupDocument request) {
    	MessageBackupDocument saved = messageBackupService.insert(request);
    	if (saved == null) {
    		throw new RuntimeException("Error saving the message backup");
    	}
    	
        return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
    }
    
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
    
    @GetMapping
    public ResponseEntity<CommonResponse<List<MessageBackupResponse>>> getMessages(@RequestParam List<String> messageIds) {
    	List<MessageBackupDocument> messageList = messageBackupService.getMessages(messageIds);     
        return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, 
        		messageList.stream().map(mb -> MessageBackupResponse.from(mb)).toList()));
    }
    
    @GetMapping("/{messageId}")
    public ResponseEntity<CommonResponse<MessageBackupResponse>> getMessage(@PathVariable String messageId) {
    	try {
    		MessageBackupDocument saved = messageBackupService.getMessage(messageId);     
    		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, MessageBackupResponse.from(saved)));

    	} catch (RecordNotFoundException ex) {
    		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponse.from(ResponseCode.MESSAGE_BACKUP_NOT_FOUND));
    	}
    }

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
    
    @DeleteMapping("/{messageId}")
    public ResponseEntity<CommonResponse<?>> deleteMessage(@PathVariable String messageId) {
    	try {
    		messageBackupService.delete(messageId);        
    		return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
    	} catch (RecordNotFoundException ex) {
    		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponse.from(ResponseCode.MESSAGE_BACKUP_NOT_FOUND));
    	}
    }
    
    @DeleteMapping("/{peerKey}/conversation")
    public ResponseEntity<CommonResponse<?>> deleteByConversation(@PathVariable String peerKey) {
        messageBackupService.deleteConversation(SecurityUtil.getUserKey(), peerKey);          
        return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
    }
    
    @DeleteMapping
    public ResponseEntity<CommonResponse<?>> deleteByUserKey() {
        messageBackupService.deleteByUserKey(SecurityUtil.getUserKey());        
        return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
    }
}
