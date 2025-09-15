package com.algomeet.notificationservice.consumer.receiver.processor;

import java.util.List;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.algomeet.notificationservice.dto.NotificationDto;
import com.algomeet.notificationservice.dto.UserContactDto;
import com.algomeet.notificationservice.dto.UserDto;
import com.algomeet.notificationservice.enums.ReceiverGroup;
import com.algomeet.notificationservice.repository.UserContactNativeRepository;
import com.algomeet.notificationservice.repository.UserNativeRepository;

import jakarta.validation.ValidationException;

@Component
public class UserFriendstReceiverGroup implements ReceiverGroupProcessor{
	@Autowired
	private UserContactNativeRepository userContactNativeRepository;
	@Autowired
	private UserNativeRepository userNativeRepository;

	@Override
	public List<UserDto> getUserList(NotificationDto notificationDto) {
		if (!(StringUtils.hasLength(notificationDto.getReceiverGroup()))
				|| !(ReceiverGroup.USER_FRIENDS.equals(ReceiverGroup.valueOf(notificationDto.getReceiverGroup().trim())))) {
			return null;
		}

		if (!(StringUtils.hasLength(notificationDto.getReceiverGroupRefId()))) {
			throw new ValidationException("Receiver group referrence id fiels is empty");
		}

		List<String> userContactList = userContactNativeRepository.getUserFriendList(notificationDto.getReceiverGroupRefId());
		List<UserDto> userList = null;
		
		if (!CollectionUtils.isEmpty(userContactList)) {
			userList = userNativeRepository.getUsersByUserKeyList(userContactList);
		}
		
		return userList;
	}
}
