package com.algomeet.authservice.otp;

import com.algomeet.authservice.client.UserClient;
import com.algomeet.authservice.config.AuthProperties;
import com.algomeet.authservice.dto.*;
import com.algomeet.authservice.service.OtpService;
import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RegistrationService {

  private final PendingRegistrationRepository pendingRepo;
  private final PasswordEncoder passwordEncoder;   // BCrypt
  private final OtpService otpService;             // you already have

  private final UserClient userClient;
  private final AuthProperties props;

  public RegisterInitResponse init(RegisterInitRequest req, String ip) {
    if ((StringUtils.hasText(req.getEmail()) ? 1 : 0) +
        (StringUtils.hasText(req.getPhone()) ? 1 : 0) == 0) {
      throw new IllegalArgumentException("Either email or phone is required");
    }

    // Pre-hash now; we never store raw anywhere
    String hash = passwordEncoder.encode(req.getPassword());

    String txn = UUID.randomUUID().toString();
    Instant now = Instant.now();
    Instant expireAt = now.plusSeconds(props.getOtp().getTtlSeconds());

    var doc = PendingRegistrationDoc.builder()
        .txn(txn)
        .username(req.getUsername())
        .email(req.getEmail())
        .phone(req.getPhone())
        .passwordHash(hash)
        .deviceId(req.getDeviceId())
        .deviceType(req.getDeviceType())
        .country(req.getCountry())
        .region(req.getRegion())
        .city(req.getCity())
        .latitude(req.getLatitude())
        .longitude(req.getLongitude())
        .createdAt(now)
        .expireAt(expireAt)
        .build();

    pendingRepo.save(doc);

    // Send OTP on chosen channel
    if (StringUtils.hasText(req.getEmail())) {
      otpService.initEmailRegistrationOtp(req.getEmail());
    } else {
      otpService.initSmsRegistrationOtp(req.getPhone());
    }
    //Email or PHONE
    return new RegisterInitResponse(txn, "EMAIL", "OTP sent.");
  }

  @Transactional(noRollbackFor = IllegalArgumentException.class)
  public AuthTokensResponse verify(RegisterVerifyRequest req, String ip) {
    var doc = pendingRepo.findById(req.getTransactionId())
        .orElseThrow(() -> new IllegalArgumentException("Invalid or expired registration txn"));

    // Verify OTP on the correct channel
    boolean ok = StringUtils.hasText(doc.getEmail())
        ? otpService.verifyEmailRegistrationOtp(doc.getEmail(), req.getCode())
        : otpService.verifySmsRegistrationOtp(doc.getPhone(), req.getCode());

    if (!ok) throw new IllegalArgumentException("Invalid or expired OTP");

    // Create user in user-service
    UserRequest create = UserRequest.builder()
        .email(doc.getEmail())
        .phone(doc.getPhone())
        .username(doc.getUsername())
        .password(doc.getPasswordHash())         // BCrypt
        .country(doc.getCountry())
        .region(doc.getRegion())
        .city(doc.getCity())
        .latitude(doc.getLatitude())
        .longitude(doc.getLongitude())
        .isEmailVerified(StringUtils.hasText(doc.getEmail()))
        .isPhoneVerified(StringUtils.hasText(doc.getPhone()))
        .registrationIp(ip)
        .registrationDeviceId(doc.getDeviceId())
        .registrationDeviceType(doc.getDeviceType())
        .loginTypePolicy(props.getAuth().getLoginTypePolicyDefault())
        .build();

    UserResponse created = (UserResponse) userClient.createUser(create);

    // Optional: bind active device (use POST if PATCH is blocked)
    try {
      if (created.getId() != null) {
        userClient.updateActiveDevice(created.getId(), doc.getDeviceId());
      }
    } catch (Exception e) {
      // log and continue; don't fail registration
      LoggerFactory.getLogger(getClass())
          .warn("bind device failed for userId={} deviceId={} err={}",
                created.getId(), doc.getDeviceId(), e.toString());
    }

    // cleanup pending doc
    pendingRepo.deleteById(req.getTransactionId());

    // tokens (subject uses email if present else phone)
    String principal = StringUtils.hasText(created.getEmail())
        ? created.getEmail() : created.getPhone();



    String type = StringUtils.hasText(doc.getEmail()) ? "EMAIL" : "PHONE";
    return new AuthTokensResponse(type, "Registration successful.");
  }
}
