// package com.algomeet.contactservice.dto;
package com.algomeet.contactservice.dto;

import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ContactActionResponse {
    private String code;         //  OK, SELF, RECEIVER_NOT_FOUND, ALREADY_FRIEND, PENDING_EXISTS, AUTO_ACCEPTED, NO_REQUEST_FOUND, ERROR
    private String message;      // human-friendly
    private RelationStatus relation; // OPTIONAL: FOUND/ALREADY_FRIEND/PENDING etc. (reuse the enum from search if you like)
    private UserDto user;        // optional: peer info for UI (e.g., show their avatar/name)
}
