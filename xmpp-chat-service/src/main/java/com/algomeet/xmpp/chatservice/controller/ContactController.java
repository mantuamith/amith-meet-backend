package com.algomeet.xmpp.chatservice.controller;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.algomeet.xmpp.chatservice.controller.doc.ContactControllerDoc;
import com.algomeet.xmpp.chatservice.dto.CommonResponse;
import com.algomeet.xmpp.chatservice.enums.ResponseCode;
import com.algomeet.xmpp.chatservice.service.UnreadCountService;
import com.algomeet.xmpp.chatservice.util.SecurityUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/chat/recent-contacts")
public class ContactController implements ContactControllerDoc{

    @Autowired
    private UnreadCountService unreadCountService;

    /**
     * Retrieves a paginated list of recent contact IDs.
     * * @param page Zero-based page index (defaults to 0 for the most recent contacts).
     * @param size Number of records per page (defaults to 20 to balance UI load and performance).
     * @return A list of unique participant keys involved in recent unread counts/interactions.
     */
    @Override
    @GetMapping
    public ResponseEntity<CommonResponse<List<String>>> getRecentContacts(
            @RequestParam(value = "page", defaultValue = "0") int page, 
            @RequestParam(value = "size", defaultValue = "20") int size) {
        
        // 1. Get the authenticated user's key
        String userKey = SecurityUtil.getUserKey();
        
        // 2. Prepare an ordered Set to handle the logic of splitting composite IDs 
        // while maintaining the 'Recent First' sort order from MongoDB.
        Set<String> recentContacts = new LinkedHashSet<>();
       
        // 3. Fetch paginated data from the reactive service.
        // We block here to convert the reactive stream into a synchronous list 
        // for this specific REST endpoint.
        List<String> recentContactList = unreadCountService.getRecentContactKeysReactive(userKey, page, size)
                .collectList()
                .block(); 

        if (recentContactList != null) {
            for (String contact : recentContactList) {
                // 5. Split composite IDs (e.g., "userA_userB") into individual keys
                String[] contactArr = contact.split("_");
                
                recentContacts.add(contactArr[0]);
                
                if (contactArr.length > 1) {
                    recentContacts.add(contactArr[1]);
                }
            }
        }
        
        // 6. Final cleanup: The user should not see themselves as a "recent contact"
        recentContacts.remove(userKey);
        
        return ResponseEntity.ok(CommonResponse.from(
                ResponseCode.SUCCESS, 
                recentContacts.stream().toList()
        ));
    }
   
}