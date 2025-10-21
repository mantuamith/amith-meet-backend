package com.algomeet.chatservice.service;

import java.util.List;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.algomeet.chatservice.document.GroupSessionMessageDocument;
import com.algomeet.chatservice.document.GroupSessionMessageResponse;
import com.algomeet.chatservice.mapper.GroupSessionMessageMapper;
import com.algomeet.chatservice.repository.GroupSessionMessageRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupSessionMessageService {
	private final GroupSessionMessageRepository groupSessionMessageRepository;
	private final SimpMessagingTemplate messagingTemplate;
	private final GroupSessionMessageMapper groupSessionMessageMapper;

	public void deliverAllPendingTo(String receiverUsername) {
		List<GroupSessionMessageDocument> unsendMessages = groupSessionMessageRepository.findByTo(receiverUsername);   
		
		for (GroupSessionMessageDocument message : unsendMessages) {
			try {    			        	
				GroupSessionMessageResponse reponse = groupSessionMessageMapper.toResponse(message);
				messagingTemplate.convertAndSendToUser(
						message.getTo(),
						"/queue/keys/group/share",
						reponse
						);
			} catch (Exception ex) {
				log.error("Failed to send group session message to {}: {}", message.getTo(), ex.getMessage());

				messagingTemplate.convertAndSendToUser(
						receiverUsername,
						"/queue/errors",
						"WebRTC group session message failed to deliver to: " + message.getTo()
						);
			}
		}
	}

}
