package com.algomeet.meetservice.repository;

import com.algomeet.meetservice.enums.MeetingType;
import com.algomeet.meetservice.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class MeetingRepositoryTest {

    @Autowired private MeetingRepository meetingRepository;
    @Autowired private RoomRepository roomRepository;

    private Meeting newMeeting(String id, String host, Room room, Instant start, Instant end) {
        Meeting m = new Meeting();
        m.setId(id);
        m.setHostEmail(host);
        m.setToken(UUID.randomUUID().toString());
        m.setMeetingType(MeetingType.MEETING);
        m.setCreatedAt(Instant.now());
        m.setExpiresAt(Instant.now().plusSeconds(3600));
        m.setMeetingStartTime(start);
        m.setMeetingEndTime(end);
        m.setMeetingName("M-" + id);
        m.setStatus(MeetingStatus.SCHEDULED);
        m.setRoom(room);
        m.setAttendees(Set.of("a@ex.com", "b@ex.com"));
        return meetingRepository.save(m);
    }

    @Test
    void finders_work() {
        Room r = roomRepository.save(Room.builder()
                .roomId("130000000111")
                .roomType(RoomType.ADHOC)
                .tenantId("tenant-1")
                .build());

        Meeting m1 = newMeeting("241001000011", "host@ex.com", r,
                Instant.now().plusSeconds(600), Instant.now().plusSeconds(1800));

        Meeting m2 = newMeeting("241001000012", "other@ex.com", r,
                Instant.now().plusSeconds(1200), Instant.now().plusSeconds(2400));

        // host view
        List<Meeting> byHost = meetingRepository.findAllByHostEmail("host@ex.com");
        assertThat(byHost).extracting(Meeting::getId).containsExactly("241001000011");

        // user view (host or attendee)
        List<Meeting> forUser = meetingRepository
                .findDistinctByHostEmailOrAttendeesContainingOrderByMeetingStartTimeAsc("host@ex.com", "a@ex.com");
        assertThat(forUser).extracting(Meeting::getId)
                .contains("241001000011", "241001000012");

        // expiry
        List<Meeting> expired = meetingRepository.findByExpiresAtBefore(Instant.now().minusSeconds(1));
        assertThat(expired).isEmpty();
    }
}
