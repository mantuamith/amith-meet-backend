package com.algomeet.notificationservice.consumer;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.algomeet.multitenancy.context.TenantContext;
import com.algomeet.notificationservice.consumer.processor.NotificationProcessor;
import com.algomeet.notificationservice.consumer.processor.NotificationProcessorProvider;
import com.algomeet.notificationservice.dto.NotificationDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class NotificationMessageHandler {
	@Autowired
	private NotificationProcessorProvider processorProvider;
		
    public void handleMessage(String message) throws SQLException {
    	NotificationDto notifDto = convertToObject(message, NotificationDto.class);

    	if(Objects.isNull(notifDto)) {
    		return;
    	}
    	
		// Switch tenant schema explicitly
		TenantContext.switchTenantExplicitly(notifDto.getTenantId());
		
    	// Retrieve notification processors
    	List<NotificationProcessor> processors = processorProvider.getProcessors();
    	for (NotificationProcessor processor : processors) {
    		processor.doProcess(notifDto);
    	}  
    	
    	// Clean-up
    	TenantContext.clear();
    }
    
    private <T> T convertToObject(String json, Class<T> t) {
    	try {
    		ObjectMapper mapper = new ObjectMapper().findAndRegisterModules(); // enables Java 8 Date/Time (Instant, LocalDateTime, etc.);
    		return mapper.readValue(json, t);
    	} catch(Exception ex) {
    		log.error("Error convering message to object {}", ex.getMessage(), ex);
    	}
    	return null;
    }
}
