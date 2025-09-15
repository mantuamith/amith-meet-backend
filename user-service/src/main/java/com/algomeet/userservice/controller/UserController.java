package com.algomeet.userservice.controller;

import com.algomeet.userservice.dto.UserDto;
import com.algomeet.userservice.enums.ResponseCode;
import com.algomeet.userservice.dto.UserRequest;
import com.algomeet.userservice.dto.UserResponse;
import com.algomeet.userservice.model.User;
import com.algomeet.userservice.model.UserProfile;
import com.algomeet.userservice.repository.UserProfileRepository;
import com.algomeet.userservice.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class UserController {


    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;
    
    private final UserProfileRepository userProfileRepository;

    // Feign client will call this from auth-service to register user
    @PostMapping
    public ResponseEntity<?> createUser(@RequestBody UserRequest request) {
        boolean emailTaken    = userRepository.existsByEmail(request.getEmail());
        boolean usernameTaken = userRepository.existsByUsername(request.getUsername());

        if (emailTaken && usernameTaken) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
            Map.of(
                    "code", ResponseCode.AUTH_DUPLICATE_BOTH.getCode(),
                    "message", ResponseCode.AUTH_DUPLICATE_BOTH.getDefaultMessage(),
                   "fields", List.of("email", "username")
            ));
        }
        if (emailTaken) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "code", ResponseCode.AUTH_DUPLICATE_EMAIL.getCode(),
                    "message", ResponseCode.AUTH_DUPLICATE_EMAIL.getDefaultMessage(),
                    "fields", List.of("email")
            ));
        }
        if (usernameTaken) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "code", ResponseCode.AUTH_DUPLICATE_USERNAME.getCode(),
                    "message", ResponseCode.AUTH_DUPLICATE_USERNAME.getDefaultMessage(),
                    "fields", List.of("username")
            ));
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());

        user.setPassword(request.getPassword()); // already BCrypted by auth-service

        user.setPhone(request.getPhone());

        user.setEmailVerified(Boolean.TRUE.equals(request.getIsEmailVerified()));
        user.setPhoneVerified(Boolean.TRUE.equals(request.getIsPhoneVerified()));
        user.setRegistrationIp(request.getRegistrationIp());

        if (request.getLoginTypePolicy()!=null)
        	user.setLoginTypePolicy(request.getLoginTypePolicy().shortValue());

        // TODO: user.setRole(...); user.setEnabled(...);
        // Generate usr key key to link users and user_profile table
        UUID userKey = UUID.randomUUID();        
        user.setUserKey(userKey); 
        
        try {
            userRepository.save(user);
            
            try {
            	// Add user profile
            	UserProfile userProfile = new UserProfile();
            	userProfile.setId(userKey);
            	userProfileRepository.save(userProfile);
            } finally {
            	// Add clean-up
            }
            
            return ResponseEntity.ok(Map.of(
                    "code", ResponseCode.AUTH_REGISTER_SUCCESS.getCode(),
                    "message", ResponseCode.AUTH_REGISTER_SUCCESS.getDefaultMessage(),
                    "user", new UserResponse(user)
            ));
        } catch (DataIntegrityViolationException ex) {
            // Safety net in case of race condition vs. DB unique constraints
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "code", ResponseCode.AUTH_DUPLICATE_BOTH.getCode(),
                    "message", "Email or username already exists"
            ));
        }
    }



    @GetMapping("/username/{username}")
    public ResponseEntity<UserResponse> getUserByUsername(@PathVariable String username) {
        return userRepository.findByUsername(username)
                .map(user -> ResponseEntity.ok(new UserResponse(user)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/lookup")
    public ResponseEntity<UserResponse> getUserByLogin(@RequestParam("login") String login) {
        String key = normalize(login);
        Optional<User> user = userRepository.findByEmail(key)
                .or(() -> userRepository.findByUsername(key));
        return user.map(u -> ResponseEntity.ok(new UserResponse(u)))
                .orElse(ResponseEntity.notFound().build());
    }


    @GetMapping("/email/{emailOrUsername}")
    public ResponseEntity<UserResponse> getUserByEmail(@PathVariable String emailOrUsername) {
        return getUserByLogin(emailOrUsername);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserDto> getUserById(@PathVariable Long id) {
        return userRepository.findById(id)
                .map(user -> ResponseEntity.ok(new UserDto(
                        String.valueOf(user.getId()),
                        user.getUsername(),
                        user.getEmail(),user.getUserKey()
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/batch")
    public ResponseEntity<List<UserDto>> getUsersByIds(@RequestBody List<String> userIds) {


        List<UserDto> users = userRepository.findAllByEmailIn(userIds)
                .stream()
                .map(user -> new UserDto(
                        String.valueOf(user.getId()),
                        user.getUsername(),
                        user.getEmail(),user.getUserKey()
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(users);
    }

    @PostMapping({"/batch/getUsersByKeys"})
    public ResponseEntity<List<UserDto>> getUsersByKeys(@RequestBody List<String> userKeys) {
        if (userKeys == null || userKeys.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        // Parse UUIDs (lenient: skip invalid entries; flip to strict by collecting bads and returning 400)
        List<UUID> uuids = new ArrayList<>(userKeys.size());
        for (String s : userKeys) {
            if (s == null || s.isBlank()) continue;
            try { uuids.add(UUID.fromString(s.trim())); } catch (IllegalArgumentException ignored) {}
        }
        if (uuids.isEmpty()) return ResponseEntity.ok(List.of());

        // Cap batch size (protect DB)
        final int MAX = 500;
        if (uuids.size() > MAX) {
            return ResponseEntity.status(413).build(); // Payload Too Large
        }

        // De-dup for query, but keep original order for output
        List<UUID> distinct = uuids.stream().distinct().toList();

        // Query DB
        List<User> users = userRepository.findByUserKeyIn(distinct);

        // Index by user_key for O(1) re-order
        Map<UUID, User> byKey = users.stream()
                .collect(java.util.stream.Collectors.toMap(User::getUserKey, u -> u));

        // Rebuild in caller's order, skipping missing users
        List<UserDto> out = new ArrayList<>(uuids.size());
        for (UUID k : uuids) {
            User u = byKey.get(k);
            if (u != null) out.add(toDto(u));
        }

        return ResponseEntity.ok(out);
    }

    @GetMapping("/search")
    public ResponseEntity<List<UserDto>> searchUsers(@RequestParam String query) {
        List<User> users = userRepository.searchUsers(query);
        List<UserDto> result = users.stream().map(user -> {
            UserDto dto = new UserDto();
            dto.setId(String.valueOf(user.getId()));
            dto.setUsername(user.getUsername());
            dto.setEmail(user.getEmail());
            return dto;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/email/{email}")
    @Transactional  //  Works as a quick fix
    public ResponseEntity<?> deleteUserByEmail(@PathVariable String email) {
        if (!userRepository.existsByEmail(email)) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }

        userRepository.deleteByEmail(email);
        return ResponseEntity.ok(Map.of("message", "User deleted successfully"));
    }

    @PostMapping("/{id}/active-device")
    public ResponseEntity<Void> updateActiveDevice(
            @PathVariable Long id, @RequestParam String deviceId) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        user.setActiveDeviceId(deviceId);
        userRepository.save(user);
        return ResponseEntity.ok().build();

    }

    @PutMapping("/{id}/password")
    public ResponseEntity<Void> updatePassword(
            @PathVariable Long id,
            @RequestParam("passwordHash") String passwordHash) {

        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        user.setPassword(passwordHash); // already BCrypted by auth-service
        userRepository.save(user);
        return ResponseEntity.ok().build();
    }


    /**
     * Start/rotate a session for this user. Sets both active_device_id and active_session_id.
     * Returns the new sid (session id).
     */
    @PostMapping("/{id}/session")
    public ResponseEntity<Map<String, String>> startSession(
            @PathVariable Long id,
            @RequestParam String deviceId,
            @RequestParam(required = false) String sid) {

        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        String newSid = (sid == null || sid.isBlank()) ? UUID.randomUUID().toString() : sid;

        user.setActiveDeviceId(deviceId);
        user.setActiveSessionId(newSid);
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("sid", newSid));
    }

    /**
     * Fetch current active sid by email (used by JWT filter to invalidate old tokens instantly).
     */
    @GetMapping("/active-sid")
    public ResponseEntity<Map<String, String>> getActiveSid(@RequestParam("email") String email) {
        return userRepository.findByEmail(email)
                .map(u -> ResponseEntity.ok(Map.of("sid", u.getActiveSessionId())))
                .orElse(ResponseEntity.notFound().build());
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    @GetMapping("/exists")
    public Map<String, Object> exists(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String phone) {

        boolean emailTaken = (email != null && !email.isBlank()) && userRepository.existsByEmail(email);
        boolean usernameTaken = (username != null && !username.isBlank()) && userRepository.existsByUsername(username);
        boolean phoneTaken = (phone != null && !phone.isBlank()) && userRepository.existsByPhone(phone);

        return Map.of(
                "emailTaken", emailTaken,
                "usernameTaken", usernameTaken,
                "phoneTaken", phoneTaken
        );
    }
    
    @PostMapping("/{id}/update-log-in-device")
    public ResponseEntity<Void> updateClientPlatformDeviceToken(
            @PathVariable Long id, @RequestParam("deviceType") Optional<String> deviceTypeOpt, 
            @RequestParam("deviceToken") Optional<String> deviceTokenOpt) {
        
    	User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
    	
    	if (deviceTypeOpt.isPresent()) {
    		user.setDeviceType(deviceTypeOpt.get()); 
    	} else {
    		user.setDeviceType(null);
    	}
    	
    	if (deviceTokenOpt.isPresent()) {
    		user.setDeviceToken(deviceTokenOpt.get()); 
    	} else {
    		user.setDeviceToken(null);
    	}
    	   	
        userRepository.save(user);
        return ResponseEntity.ok().build();

    }

    @GetMapping("/lookup/exact")
    public Optional<User> exact(@RequestParam("q") String qRaw) {
        final String q = qRaw == null ? "" : qRaw.trim();
        if (q.isEmpty()) return Optional.empty();

        // Try most likely first, then fall through
        // 1) email?
        if (q.indexOf('@') > 0) {
            var byEmail = userRepository.findByEmailIgnoreCase(q);
            if (byEmail.isPresent())
                return byEmail;
        }

        // 2) username (your “userId” string)
        var byUsername = userRepository.findByUsernameIgnoreCase(q);
        if (byUsername.isPresent()) return byUsername;

        // 3) last chance: if not email but they typed an email-looking value missing case
        if (q.indexOf('@') > 0) {
            return userRepository.findByEmailIgnoreCase(q);
        }
        return Optional.empty();
    }

    private static UserDto toDto(User u) {
        UserDto dto = new UserDto();
        dto.setId(u.getUserKey().toString());   // expose UUID as id
        dto.setUsername(u.getUsername());
        dto.setEmail(u.getEmail());
        return dto;
    }
}



//TODO: Add Service layer
