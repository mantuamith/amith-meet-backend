// package com.algomeet.contactservice.dto;
package com.algomeet.contactservice.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchUserResponse {
    private String code;           // e.g., "OK", "ALREADY_FRIEND", "PENDING", "SELF", "NOT_FOUND", "EMPTY_QUERY"
    private String message;        // human-friendly message for the FE to display
    private RelationStatus relation;
    private UserDto user;          // present only when relation == FOUND
}
