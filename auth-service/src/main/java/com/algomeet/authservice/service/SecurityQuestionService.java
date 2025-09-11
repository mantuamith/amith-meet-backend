package com.algomeet.authservice.service;

import java.util.Arrays;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;

import com.algomeet.authservice.client.SecurityQuestionClient;
import com.algomeet.authservice.dto.SecurityQuestionRequest;
import com.algomeet.authservice.dto.SecurityQuestionResponse;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Service
public class SecurityQuestionService {
	private final SecurityQuestionClient securityQuestionClient;
	
	// Create
    public SecurityQuestionResponse create(SecurityQuestionRequest request) {
        return securityQuestionClient.create(request).getBody();
    }

    // Get by ID
    public SecurityQuestionResponse getById(String id) {    	
    	try {
    		return securityQuestionClient.getById(id).getBody();
    	} catch(FeignException ex) {
    		if(ex.status() != HttpStatus.NOT_FOUND.value()) {
    			throw ex;
    		}
    	}
        return null;
    }

    // Get all
    @GetMapping
    public List<SecurityQuestionResponse> getAll() {
    	List<SecurityQuestionResponse> allQuestions = securityQuestionClient.getAll().getBody();
    	if (CollectionUtils.isEmpty(allQuestions)) {
    		try {
    			for (SecurityQuestionRequest req : defaultQuestions()) {
    				create(req);
    			}
    		} catch (Exception ex) {
    			log.error("Error adding default security questions ", ex.getMessage(), ex);
    		}
    		
    		// Retrieve the newly added questions
    		allQuestions = securityQuestionClient.getAll().getBody();
    	}
    	
    	return allQuestions;    	
    }

    // Update (PUT = full replace)
    public SecurityQuestionResponse update(
            String id,
            SecurityQuestionRequest request) {
        return securityQuestionClient.update(id, request).getBody();
    }

    // Delete
    public void delete(String id) {
    	securityQuestionClient.delete(id);
    }
    
    // Static default initialization
    public static List<SecurityQuestionRequest> defaultQuestions() {
        return Arrays.asList(
            new SecurityQuestionRequest("q1", "What is your pet's name?"),
            new SecurityQuestionRequest("q2", "What is your mother’s maiden name?"),
            new SecurityQuestionRequest("q3", "What was your first school?"),
            new SecurityQuestionRequest("q4", "What is your favorite color?"),
            new SecurityQuestionRequest("q5", "What is your birthplace?")
        );
    }    
}
