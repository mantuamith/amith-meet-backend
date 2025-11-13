package com.algomeet.authservice.service;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.algomeet.authservice.client.UserE2eeSettingClient;
import com.algomeet.authservice.dto.UserE2eeSettingRequest;
import com.algomeet.authservice.dto.UserE2eeSettingResponse;

import feign.FeignException;

@Service
public class UserE2eeSettingService {

    private final UserE2eeSettingClient client;

    public UserE2eeSettingService(UserE2eeSettingClient client) {
        this.client = client;
    }

    /**
     * Get a single user setting by userKey
     */
    public UserE2eeSettingResponse getUserSettingById(UUID userKey) {
    	try {
    		return client.getById(userKey); 
    	} catch (FeignException.NotFound e) {
    		throw new ResponseStatusException(HttpStatus.NOT_FOUND, "E2ee user setting not found", e);
    	} catch (FeignException e) {
    		throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Upstream error calling user service (" + e.status() + ")", e);
    	} catch (Exception e) {
    		throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to retrieve e2ee user setting", e);
    	}
    }

    /**
     * Create or update a user setting
     */
    public UserE2eeSettingResponse createOrUpdateUserSetting(UUID userKey, UserE2eeSettingRequest request) {
    	try {
    		return client.createOrUpdate(userKey, request);
    	} catch (FeignException.NotFound e) {
    		throw new ResponseStatusException(HttpStatus.NOT_FOUND, "E2ee user setting not found", e);
    	} catch (FeignException.BadRequest e) {
    		throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid e2ee user setting update payload", e);
    	} catch (FeignException e) {
    		throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Upstream error calling user service (" + e.status() + ")", e);
    	} catch (Exception e) {
    		throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to update e2ee user setting", e);
    	}
    }

    /**
     * Delete a user setting by userKey
     */
    public void deleteUserSetting(UUID userKey) {
    	try {
    		client.delete(userKey);
    	} catch (FeignException.NotFound e) {
    		throw new ResponseStatusException(HttpStatus.NOT_FOUND, "E2ee user setting not found", e);
    	} catch (FeignException e) {
    		throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Upstream error calling user service (" + e.status() + ")", e);
    	} catch (Exception e) {
    		throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to delete e2ee user setting", e);
    	}
    }
}
