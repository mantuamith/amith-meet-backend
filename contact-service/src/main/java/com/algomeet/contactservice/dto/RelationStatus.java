// package com.algomeet.contactservice.dto;
package com.algomeet.contactservice.dto;

public enum RelationStatus {
    FOUND,          // user exists and can be added
    ALREADY_FRIEND, // already in accepted list
    PENDING,        // friend request already sent/received
    SELF,           // searching yourself
    NOT_FOUND,      // user not found
    EMPTY_QUERY     // blank query
}
