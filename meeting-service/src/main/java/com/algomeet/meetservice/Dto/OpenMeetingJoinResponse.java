// com.algomeet.meetservice.dto.OpenMeetingJoinResponse
package com.algomeet.meetservice.Dto;

import com.algomeet.meetservice.model.Meeting;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class OpenMeetingJoinResponse {
    private Meeting meeting;       // your full meeting object
    private String algomeetToken;  // JWT to pass into Algo meet join
    private String room;           // room name used for join
    private Instant tokenExpiresAt;
}
