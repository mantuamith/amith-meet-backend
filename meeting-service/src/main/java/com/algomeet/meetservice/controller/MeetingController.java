package com.algomeet.meetservice.controller;

import com.algomeet.meetservice.model.Meeting;
import com.algomeet.meetservice.service.MeetingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/meetings")
public class MeetingController {

    @Autowired
    private MeetingService meetingService;

    @PostMapping("/create")
    public ResponseEntity<Meeting> createMeeting() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            System.err.println("[SECURITY DEBUG] Authentication object is null");
        } else {
            System.out.println("[SECURITY DEBUG] Principal: " + authentication.getPrincipal());
            System.out.println("[SECURITY DEBUG] Is Authenticated: " + authentication.isAuthenticated());
        }

        String email = authentication.getName(); // or getPrincipal().toString()
        return ResponseEntity.ok(meetingService.createMeeting(email));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Meeting> getMeeting(@PathVariable String id) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Optional<Meeting> meeting = meetingService.getMeetingById(id, email);
        return meeting.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        String user = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok("Hello " + user);
    }

    @GetMapping
    public ResponseEntity<List<Meeting>> getMeetings() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(meetingService.getMeetingsByHostEmail(email));
    }
}
