package com.algomeet.meetservice.mapper;

import com.algomeet.meetservice.Dto.MeetingDto;
import com.algomeet.meetservice.Dto.RoomDto;
import com.algomeet.meetservice.model.Meeting;
import com.algomeet.meetservice.model.Room;
import com.algomeet.meetservice.service.LinkFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
@Component
@RequiredArgsConstructor
public  class MeetingMapper {

    @Autowired
    private final LinkFactory links;

    public MeetingDto toDto(Meeting m) {

        String url = links.inviteUrl(m.getId(), m.getToken());
        return new MeetingDto(
            m.getId(),
            m.getMeetingType() == null ? null : m.getMeetingType().name(),
            m.getHostEmail(),
            m.getStatus() == null ? null : m.getStatus().name(),
            m.getToken(),
            m.getMeetingStartTime(),
            m.getMeetingEndTime(),
            roomToDto(m.getRoom()),
            m.getHostName(),
            m.getMeetingName(),
            m.getMeetingDescription(),
            m.isLobbyEnabled(),
            m.isReminderEnabled(),
            m.getReminderMinutes() == null ? 0 : m.getReminderMinutes(),
            m.getAttendees() == null ? null : new ArrayList<>(m.getAttendees()),
            m.getInvitedParticipants() == null ? null : new ArrayList<>(m.getInvitedParticipants()),
                url,
                m.isPasswordEnabled(), m.getPasswordHash(), m.getModeratorPassword()

        );
    }

    private static RoomDto roomToDto(Room r) {
        if (r == null) return null;
        return new RoomDto(
            r.getRoomId(),
            r.getRoomType() == null ? null : r.getRoomType().name(),
            r.getOwnerEmail(),
            r.isLobbyDefault(),
            r.isRecordingDefault()
        );
    }
}
