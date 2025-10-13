package com.algomeet.authservice.dto;

import java.util.UUID;

import com.algomeet.authservice.constants.Constants;
import com.algomeet.authservice.util.MessageUtil;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserSecurityQuestionResponse {
    private int id;
    private UUID userProfileId;
    private String securityQuestionId;
    private String question;
    private String answer;
    
    public String getQuestion() {
    	return getI18n(this.securityQuestionId, this.question);
    }

    private String getI18n(String securityQuestionId, String defaultDesc) {    	
    	try {
    		return MessageUtil.getMessage(Constants.SECURITY_QUESTION_MESSAGE_PROPERTY_KEY_PREFIX + securityQuestionId);
    	} catch(Exception ex) {
    		return defaultDesc;
    	}
    }
}