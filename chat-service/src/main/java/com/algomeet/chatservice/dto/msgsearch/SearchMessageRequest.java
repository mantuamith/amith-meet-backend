package com.algomeet.chatservice.dto.msgsearch;

import lombok.Data;

@Data
public class SearchMessageRequest {
    private String q;           // required: the search text
    private String otherUser;   // optional: narrow to a 1:1 thread
    private Integer page = 0;   // optional
    private Integer size = 20;  // optional (cap at 100 in service)
}
