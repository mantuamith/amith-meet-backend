
package com.algomeet.meetservice.Dto;

import com.algomeet.meetservice.model.RecurrenceType;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class EditMeetingRequest {
    private String meetingName;           // null = no change
    private String meetDescription;       // null = no change
    private Instant meetingStartTime;     // null = no change
    private Instant meetingEndTime;       // null = no change
    private List<String> attendees;       // replaces invited list if provided
    private RecurrenceType recurrence;    // null = no change
    private Boolean reminderEnabled;      // null = no change
    private Integer reminderMinutes;      // null = no change
    private Boolean lobbyEnabled;         // null = no change
}
