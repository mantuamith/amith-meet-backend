package com.algomeet.userservice.dto;

import com.algomeet.userservice.model.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
public class UserResponse {
    private Long id;
    private String username;
    private String email;
    private String password;
    private String role;
    private boolean enabled;

    public UserResponse(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.email = user.getEmail();
        this.password = user.getPassword();

    }


}
