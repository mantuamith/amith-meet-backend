package com.algomeet.meetservice.model;

import com.algomeet.meetservice.enums.MeetingType;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Data
@Entity
public class Meeting {

    @Id
    private String id;

    @Column(name="meeting_type", nullable=true, length=20)
    @Enumerated(EnumType.STRING)
    private MeetingType meetingType = MeetingType.MEETING;

    private String token;

    private String hostEmail;  // Added field


    private Instant createdAt;

    private Instant expiresAt;

    private String password; // Optional meeting password

    private String meetingName; // Friendly meeting title

    @Column(name = "meeting_start_time", nullable = false)  // renamed from meeting_time
    private Instant meetingStartTime; // was meetingTime

    @Column(name = "meeting_end_time")
    private Instant meetingEndTime;   // NEW

    @Column(name = "meeting_description")
    private String meetingDescription = "AlgoMeet Meeting -- Default Description";

    @Column (name = "password_enable", nullable = true)
    private boolean passwordEnabled = false;

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
