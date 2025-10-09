package com.algomeet.authservice.dto;
import com.algomeet.authservice.constants.Constants;
import com.algomeet.authservice.util.MessageUtil;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SecurityQuestionResponse {
    private String id;
    private String question;
    
    public String getQuestion() {
    	return getI18n(this.id, this.question);
    }

    private String getI18n(String securityQuestionId, String defaultDesc) {    	
    	try {
    		return MessageUtil.getMessage(Constants.SECURITY_QUESTION_MESSAGE_PROPERTY_KEY_PREFIX + securityQuestionId);
    	} catch(Exception ex) {
    		return defaultDesc;
    	}
    }
}