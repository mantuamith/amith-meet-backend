package com.algomeet.authservice.support;

import com.algomeet.authservice.dto.*;
import com.algomeet.authservice.enums.ResponseCode;



import java.util.UUID;


public class TestData {
    public static UserResponse userResponse() {
        UserResponse u = new UserResponse();
        u.setId(123L);
        u.setEmail("alice@example.com");
        u.setUsername("alice");
        u.setRole("USER");
        u.setUserKey(UUID.randomUUID());
        u.setEnabled(true);
// set any other required fields in your DTO
        return u;
    }


    public static CommonResponse<UserResponse> okUser(UserResponse u){
        CommonResponse<UserResponse> r = new CommonResponse<>();
        r.setCode(ResponseCode.SUCCESS.name());
        r.setMessage("OK");
        r.setData(u);
        return r;
    }
}
