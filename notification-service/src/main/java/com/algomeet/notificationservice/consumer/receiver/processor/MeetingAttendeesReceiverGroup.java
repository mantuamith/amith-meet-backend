package com.algomeet.notificationservice.consumer.receiver.processor;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.algomeet.notificationservice.dto.NotificationDto;
import com.algomeet.notificationservice.dto.UserDto;
import com.algomeet.notificationservice.enums.ReceiverGroup;
import com.algomeet.notificationservice.repository.MeetingNativeRepository;
import com.algomeet.notificationservice.repository.UserNativeRepository;

import jakarta.validation.ValidationException;

@Component
public class MeetingAttendeesReceiverGroup implements ReceiverGroupProcessor{
	@Autowired
	private MeetingNativeRepository meetingNativeRepository;
	@Autowired
	private UserNativeRepository userNativeRepository;

	@Override
	public List<UserDto> getUserList(NotificationDto notificationDto) {
		if (!(StringUtils.hasLength(notificationDto.getReceiverGroup()))
				|| ReceiverGroup.MEETING_ATTENDEES != ReceiverGroup.valueOf(notificationDto.getReceiverGroup())) {
			return null;
		}

		if (!(StringUtils.hasLength(notificationDto.getReceiverGroupRefId()))) {
			throw new ValidationException("Receiver group reference id field is empty");
		}

		List<String> userAttendeesList = meetingNativeRepository.getParticipantList(notificationDto.getReceiverGroupRefId());
		List<UserDto> userList = null;
		
		if (!CollectionUtils.isEmpty(userAttendeesList)) {
			userList = userNativeRepository.getUsersByEmailList(userAttendeesList);
		}
		
		return userList;
	}
}
