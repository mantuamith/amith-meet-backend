package com.algomeet.meetservice.Dto;

/**
 * Centralized response code constants used across controllers/services.
 */
public final class ResponseCodes {
    public static final String MEETING_NOT_FOUND = "MEETING_NOT_FOUND";

    private ResponseCodes() {
    }

    public static final String PASSWORD_REQUIRED = "PASSWORD_REQUIRED";
    public static final String PASSWORD_INCORRECT = "PASSWORD_INCORRECT";
    public static final String TOKEN_REQUIRED = "TOKEN_REQUIRED";
    public static final String MEETING_JOINED_SUCCESS = "MEETING_JOINED_SUCCESS";
    public static final String MEETING_ACCESS_DENIED = "MEETING_ACCESS_DENIED";
    public static final String MEETING_NOT_STARTED = "MEETING_NOT_STARTED";
    public static final String MEETING_COMPLETED = "MEETING_COMPLETED";
    public static final String MEETING_EXPIRED = "MEETING_EXPIRED";
    public static final String MEETING_FETCH_SUCCESS = "MEETING_FETCH_SUCCESS";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    public static final String SUCCESS = "SUCCESS";
    public static final String OK = "OK";
    public static final String MEETING_COMPLETE_FAILED = "MEETING_COMPLETE_FAILED";
    public static final String MEETING_UPDATE_SUCCESS = "MEETING_UPDATE_SUCCESS";
    public static final String MEETING_DELETE_SUCCESS = "MEETING_DELETE_SUCCESS";
    public static final String MEETING_PARTICIPANT_APPROVED = "MEETING_PARTICIPANT_APPROVED";
    public static final String MEETING_PARTICIPANT_REJECTED = "MEETING_PARTICIPANT_REJECTED";
}