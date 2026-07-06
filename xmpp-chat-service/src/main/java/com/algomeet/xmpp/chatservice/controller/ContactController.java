package com.algomeet.xmpp.chatservice.controller;

import java.util.List;

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
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/chat/recent-contacts")
public class ContactController implements ContactControllerDoc {

    @Autowired
    private UnreadCountService unreadCountService;

    /**
     * Retrieves a paginated list of recent contact IDs reactively.
     */
    @Override
    @GetMapping
    public Mono<ResponseEntity<CommonResponse<List<String>>>> getRecentContacts(
            @RequestParam(value = "page", defaultValue = "0") int page, 
            @RequestParam(value = "size", defaultValue = "20") int size) {
        
        String userKey = SecurityUtil.getUserKey();
        
        // Process the underlying continuous dynamic flux pipeline completely non-blockingly
        return unreadCountService.getRecentContactKeysReactive(userKey, page, size) // Returns Flux<String>
                // 1. Map individual composite items (e.g. "userA_userB") into distinct arrays
                .map(contact -> contact.split("_"))
                // 2. Flatten arrays into a sequential stream of standalone keys, maintaining order
                .flatMapIterable(List::of)
                // 3. Native deduplication: drops duplicates while preserving insertion order (like a LinkedHashSet)
                .distinct()
                // 4. Structural cleanup rule: The user should not see themselves as a "recent contact"
                .filter(contactKey -> !contactKey.equals(userKey))
                // 5. Gather all the remaining valid matching keys into a clean, bound List
                .collectList()
                // 6. Map the final collected list into your standard HTTP envelope
                .map(cleanContactList -> ResponseEntity.ok(
                        CommonResponse.from(ResponseCode.SUCCESS, cleanContactList)
                ))
                .onErrorReturn(ResponseEntity.status(500)
                        .body(CommonResponse.from(ResponseCode.ERROR, List.of())));
    }
}