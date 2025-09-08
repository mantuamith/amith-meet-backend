package com.algomeet.userservice.controller;

import com.algomeet.userservice.dto.UserDto;
import com.algomeet.userservice.enums.ResponseCode;
import com.algomeet.userservice.dto.UserRequest;
import com.algomeet.userservice.dto.UserResponse;
import com.algomeet.userservice.model.User;
import com.algomeet.userservice.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class UserController {


    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

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
          user.setCountry(request.getCountry());
          user.setRegion(request.getRegion());
          user.setCity(request.getCity());
           if (request.getLatitude()!=null)
               user.setLatitude(request.getLatitude());
           if (request.getLongitude()!=null)
               user.setLongitude(request.getLongitude());
           user.setEmailVerified(Boolean.TRUE.equals(request.getIsEmailVerified()));
           user.setPhoneVerified(Boolean.TRUE.equals(request.getIsPhoneVerified()));
           user.setRegistrationIp(request.getRegistrationIp());
           user.setRegistrationDeviceId(request.getRegistrationDeviceId());
           user.setRegistrationDeviceType(request.getRegistrationDeviceType());
           if (request.getLoginTypePolicy()!=null)
                 user.setLoginTypePolicy(request.getLoginTypePolicy().shortValue());

        // TODO: user.setRole(...); user.setEnabled(...);

        try {
            userRepository.save(user);
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
                        user.getEmail(),null
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
                        user.getEmail()
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(users);
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

    @GetMapping("/lookup/exact")
    public ResponseEntity<UserDto> exact(@RequestParam("q") @NotBlank String qRaw) {
        final String q = qRaw.trim();
        if (q.isEmpty()) return ResponseEntity.badRequest().build();

        Optional<User> hit;

        // 1) email first if it looks like an email
        if (q.indexOf('@') > 0) {
            hit = userRepository.findByEmailIgnoreCase(q);
            if (hit.isPresent()) return ResponseEntity.ok(toDto(hit.get()));
            // fall through to username if email miss
        }

        // 2) username (case-insensitive)
        hit = userRepository.findByUsernameIgnoreCase(q);
        if (hit.isPresent()) return ResponseEntity.ok(toDto(hit.get()));

        // 3) UUID (user_key) if parsable
        try {
            hit = userRepository.findByUserKey(UUID.fromString(q));
            if (hit.isPresent()) return ResponseEntity.ok(toDto(hit.get()));
        } catch (IllegalArgumentException ignore) {
            // not a UUID, ignore
        }

        // 4) nothing matched
        return ResponseEntity.notFound().build();
    }

    // Optional: batch exact lookups (useful for client-side validations)
    @PostMapping("/lookup/exact-batch")
    public ResponseEntity<java.util.Map<String, UserDto>> exactBatch(@RequestBody java.util.List<String> queries) {
        java.util.Map<String, UserDto> out = new java.util.LinkedHashMap<>();
        if (queries == null) return ResponseEntity.ok(out);

        for (String qRaw : queries) {
            if (qRaw == null) continue;
            String q = qRaw.trim();
            if (q.isEmpty()) continue;

            var dto = exactInternal(q).orElse(null);
            if (dto != null) out.put(qRaw, dto);  // only include hits
        }
        return ResponseEntity.ok(out);
    }

    @PostMapping("/batch/keys")
    public List<UserDto> getByUserKeys(@RequestBody List<String> keys) {
        if (keys == null || keys.isEmpty()) return List.of();

        // parse UUIDs; ignore invalids or throw 400 (pick one)
        List<UUID> uuids = keys.stream()
                .map(k -> {
                    try { return UUID.fromString(k); } catch (IllegalArgumentException e) { return null; }
                })
                .filter(Objects::nonNull)
                .toList();

        var users = userRepository.findAllByUserKeyIn(uuids);

        // Preserve input order; skip missing keys
        Map<UUID, UserDto> byKey = users.stream()
                .collect(Collectors.toMap(User::getUserKey, mapper::toDto));
        List<UserDto> ordered = new ArrayList<>(uuids.size());
        for (UUID k : uuids) {
            UserDto dto = byKey.get(k);
            if (dto != null) ordered.add(dto);
        }
        return ordered;
    }

    // ---- helpers ----

    private Optional<UserDto> exactInternal(String q) {
        Optional<User> hit;

        if (q.indexOf('@') > 0) {
            hit = userRepository.findByEmailIgnoreCase(q);
            if (hit.isPresent()) return hit.map(this::toDto);
        }
        hit = userRepository.findByUsernameIgnoreCase(q);
        if (hit.isPresent()) return hit.map(this::toDto);

        try {
            hit = userRepository.findByUserKey(UUID.fromString(q));
            if (hit.isPresent()) return hit.map(this::toDto);
        } catch (IllegalArgumentException ignore) { }

        return Optional.empty();
    }

    private UserDto toDto(User u) {
        // Keep ID = UUID (user_key) to be stable across renames
        return UserDto.builder()
                .id(u.getUserKey().toString())
                .username(u.getUsername())
                .email(u.getEmail())
                .build();
    }

}

//TODO: Add Service layer
