package com.algomeet.meetservice.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
@Entity
public class Meeting {

    @Id
    private String id;

    private String token;

    private String hostEmail;  // Added field

    private Instant createdAt;

    private Instant expiresAt;

    private String password; // Optional meeting password

    private String meetingName; // Friendly meeting title

    private Instant meetingTime; // Scheduled start time

    @Enumerated(EnumType.STRING)
    private MeetingStatus status = MeetingStatus.SCHEDULED;

    @ElementCollection
    private Set<String> invitedParticipants = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "meeting_attendees", joinColumns = @JoinColumn(name = "meeting_id"))
    @Column(name = "attendee_email")
    private Set<String> attendees = new HashSet<>();

    @Enumerated(EnumType.STRING)
    private RecurrenceType recurrence = RecurrenceType.NONE; // Recurrence setting

    @Column(nullable = true)
    private boolean reminderEnabled = false; // Enabled by default

    @Column(name = "reminder_minutes", nullable = false)
    private Integer reminderMinutes = 10;   // Default 10 minutes before

    @Column(name = "lobby_enabled", nullable = true)
    private boolean lobbyEnabled;  // Waiting room feature

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "meeting_pending_participants", joinColumns = @JoinColumn(name = "meeting_id"))
    @Column(name = "email")
    private Set<String> pendingParticipants = new HashSet<>();
}
