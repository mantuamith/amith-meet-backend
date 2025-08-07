package com.algomeet.meetservice.Dto;

import com.algomeet.meetservice.model.Meeting;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MeetingResponse<T> {
    private String code;
    private String message;
    private T data;

    public static <T> MeetingResponse<T> success(String code, String message, T data) {
        return new MeetingResponse<>(code, message, data);
    }

    public static <T> MeetingResponse<T> error(String code, String message) {
        return new MeetingResponse<>(code, message, null);
    }
}
