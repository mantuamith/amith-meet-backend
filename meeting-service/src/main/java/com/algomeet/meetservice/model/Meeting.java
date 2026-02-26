package com.algomeet.meetservice.model;

import com.algomeet.meetservice.enums.MeetingType;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Data
@Entity
public class Meeting {

    @Id
    private String id;

    @Column(name="meeting_type", nullable=true, length=20)
    @Enumerated(EnumType.STRING)
    private MeetingType meetingType = MeetingType.MEETING;

    private String token;

    private String hostEmail;
    private String hostName;// Added field
    private String moderatorPassword;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    /** BCrypt hash of the meeting password (if enabled). */
    @Column(name = "password_hash")
    private String passwordHash;

    private Instant createdAt;

    private Instant expiresAt;



    private String meetingName; // Friendly meeting title

    @Column(name = "meeting_start_time", nullable = false)  // renamed from meeting_time
    private Instant meetingStartTime; // was meetingTime

    private UUID  hostUserKey;

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

//**
//ALTER TABLE meeting
//  ADD COLUMN IF NOT EXISTS algomeet_room VARCHAR(64);
//
//-- Keep old behavior for existing rows
//UPDATE meeting SET algomeet_room = id WHERE jitsi_room IS NULL;
//
//
//
//
//
// ?//
