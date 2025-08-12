package com.algomeet.meetservice.Dto;

import com.algomeet.meetservice.model.RecurrenceType;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class MeetingRequest {

    private String meetingName;       // Friendly meeting title

    private String password;          // Optional password

    private Instant meetingStartTime;      // Scheduled start time

    private Instant meetingEndTime;

    private String meetDescription;

    private List<String> attendees;   // List of attendee emails

    private RecurrenceType recurrence = RecurrenceType.NONE; // NONE, WEEKLY, MONTHLY

    private boolean reminderEnabled = true; // Default: enabled

    private Integer reminderMinutes = 10;   // Default: 10 minutes before

    private boolean lobbyEnabled = false;   // Default: disabled

    private  boolean passwordEnabled = false;
}
