package com.algomeet.userservice.service;

import org.springframework.stereotype.Service;

import com.algomeet.multitenancy.annotations.UsePublicSchema;
import com.algomeet.userservice.model.User;
import com.algomeet.userservice.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * This service is used to bypass the AOP limitation that won't invoke more than once on
 * a nested method calls when method called is declared within the same class.
 */
@RequiredArgsConstructor
@Service
public class UserService {
	private final UserRepository userRepository;
	
	@UsePublicSchema
	public User save(User user) {
		return userRepository.save(user);
	}
}
