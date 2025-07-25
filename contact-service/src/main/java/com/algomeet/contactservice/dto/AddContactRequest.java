package com.algomeet.contactservice.dto;

import lombok.Data;

@Data
public class AddContactRequest {
    private String contactUserId;
    private String senderId;
}