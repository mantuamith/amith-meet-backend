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
@RequestMapping("/api/muc/messages")
public class MucMessageController implements MucMessageControllerDoc{
	private final MucMessageService mucMessageService;

	@GetMapping("/{groupId}")
	public ResponseEntity<CommonResponse<List<MucMessageResponse>>> getMessages(
			@PathVariable UUID groupId,
			@RequestParam("beforeStanzaId") Optional<String> beforeStanzaId,
			@RequestParam("afterStanzaId") Optional<String> afterStanzaId,    		
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
			messages = mucMessageService.getMessagesAfter(UUID.fromString(userKey), 
					groupId, 
					UUID.fromString(beforeStanzaId.get()), 
					page, size);   
		}

		return ResponseEntity.ok(CommonResponse.from(
				ResponseCode.SUCCESS, 
				messages
				));
	}   

	@GetMapping("/{groupId}/updates")
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
}