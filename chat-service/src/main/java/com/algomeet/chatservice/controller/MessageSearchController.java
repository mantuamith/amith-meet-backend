package com.algomeet.chatservice.controller;

import com.algomeet.chatservice.dto.msgsearch.SearchMessageRequest;
import com.algomeet.chatservice.dto.msgsearch.SearchMessageResponse;
import com.algomeet.chatservice.service.MessageSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/messages/search")
@RequiredArgsConstructor
public class MessageSearchController {

    private final MessageSearchService messageSearchService;

    // Simple GET style: /api/messages/search?q=hello&otherUser=alice&page=0&size=20
    @GetMapping
    public List<SearchMessageResponse> search(
            @RequestParam("q") String q,
            @RequestParam(value = "otherUser", required = false) String otherUser,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        final String viewer = SecurityContextHolder.getContext().getAuthentication().getName();
        return messageSearchService.search(viewer, otherUser, q, page, size);
    }

    // Optional JSON POST for complex clients
    @PostMapping
    public List<SearchMessageResponse> searchPost(@RequestBody SearchMessageRequest req) {
        final String viewer = SecurityContextHolder.getContext().getAuthentication().getName();
        return messageSearchService.search(
                viewer,
                req.getOtherUser(),
                req.getQ(),
                req.getPage() != null ? req.getPage() : 0,
                req.getSize() != null ? req.getSize() : 20
        );
    }
}
