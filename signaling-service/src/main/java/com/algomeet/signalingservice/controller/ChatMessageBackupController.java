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
import com.algomeet.signalingservice.service.MessageBackupService;
import com.algomeet.signalingservice.util.SecurityUtil;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/signaling/backup/chat-messages")
@RequiredArgsConstructor
public class ChatMessageBackupController {
	private final MessageBackupService messageBackupService;

    @PostMapping
    public ResponseEntity<CommonResponse<?>> saveBackup(@RequestBody MessageBackupDocument request) {
    	MessageBackupDocument saved = messageBackupService.insert(request);
    	if (saved == null) {
    		throw new RuntimeException("Error saving the message backup");
    	}
    	
        return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
    }

    @GetMapping("/{senderKey}/by-sender-key")
    public ResponseEntity<CommonResponse<Page<MessageBackupResponse>>> getBackupsByUserKeyAnSenderKey(
            @PathVariable String senderKey,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Page<MessageBackupDocument> backupsPage =
                messageBackupService.getBackupsByUserKeyAnSenderKey(SecurityUtil.getUserKey(), senderKey, page, size);

        List<MessageBackupResponse> responseList = backupsPage.getContent()
                .stream()
                .map(MessageBackupResponse::from)
                .collect(Collectors.toList());

        Page<MessageBackupResponse> responsePage =
                new PageImpl<>(responseList, PageRequest.of(page, size), backupsPage.getTotalElements());

        return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS, responsePage));
    }

    @PutMapping("/{messageId}")
    public ResponseEntity<MessageBackupResponse> updateBackup(@PathVariable String messageId, 
    		@RequestBody MessageBackupDocument request) {
    	MessageBackupDocument saved = messageBackupService.update(messageId, request);
        if (saved == null) {
    		throw new RuntimeException("Error updating the message backup");
    	}
        
        return ResponseEntity.ok(MessageBackupResponse.from(request));
    }
    
    @DeleteMapping("/{messageId}")
    public ResponseEntity<CommonResponse<?>> deleteBackup(@PathVariable String messageId) {
        messageBackupService.delete(messageId);        
        return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
    }
    
    @DeleteMapping("/{senderKey}/by-sender-key")
    public ResponseEntity<CommonResponse<?>> deleteByUserKeyAndSenderKey(@PathVariable String senderKey) {
        messageBackupService.deleteByUserKeyAnSenderKey(SecurityUtil.getUserKey(), senderKey);          
        return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
    }
    
    @DeleteMapping
    public ResponseEntity<CommonResponse<?>> deleteByUserKey() {
        messageBackupService.deleteByUserKey(SecurityUtil.getUserKey());        
        return ResponseEntity.ok(CommonResponse.from(ResponseCode.SUCCESS));
    }
}
