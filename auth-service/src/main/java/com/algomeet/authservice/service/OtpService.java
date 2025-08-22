package com.algomeet.authservice.service;

import com.algomeet.authservice.otp.Otp;
import com.algomeet.authservice.otp.OtpRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private final OtpRepository otpRepository;
    private final Clock clock;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(); // only for hashing codes

    // from application.yml -> otp.*
    private final int ttlSeconds = 300;           // or inject via @Value("${otp.ttlSeconds}")
    private final int maxAttempts = 5;
    private final String pepper = "CHANGE_ME_ADD_SECRET_PEPPER"; // inject from props in your real code

    private static final SecureRandom RNG = new SecureRandom();

    private String generateNumericCode(int digits) {
        int bound = (int) Math.pow(10, digits);
        int base  = (int) Math.pow(10, digits - 1);
        int n = RNG.nextInt(bound - base) + base; // ensure fixed length
        return String.valueOf(n);
    }

    public String initEmailLoginOtp(String email) {
        String code = generateNumericCode(6);
        persistOtp(email, "EMAIL", "LOGIN", code);
        // send via your Email client (stub/log)
        log.info("OTP: email code dispatched to {}", mask(email));
        return "OTP sent to your email";
    }

    public String initSmsLoginOtp(String phone) {
        String code = generateNumericCode(6);
        persistOtp(phone, "PHONE", "LOGIN", code);
        // send via your SMS client (stub/log)
        log.info("OTP: sms code dispatched to {}", mask(phone));
        return "OTP sent to your phone";
    }

    public boolean verifyEmailLoginOtp(String email, String code) {
        return verifyOtp(email, "LOGIN", code, "EMAIL");
    }

    public boolean verifySmsLoginOtp(String phone, String code) {
        return verifyOtp(phone, "LOGIN", code, "PHONE");
    }

    private void persistOtp(String recipient, String channel, String purpose, String rawCode) {
        Instant now = Instant.now(clock);
        Instant exp = now.plus(ttlSeconds, ChronoUnit.SECONDS);
        String codeHash = encoder.encode(rawCode + pepper);

        Otp otp = Otp.builder()
                .recipient(recipient)
                .channel(channel)
                .purpose(purpose)
                .codeHash(codeHash)
                .attempts(0)
                .createdAt(now)
                .expiresAt(exp)
                .build();

        otpRepository.save(otp);
        // NOTE: do not log raw code in prod; for dev you may log it:
        log.debug("OTP DEV ONLY: recipient={} channel={} code={}", mask(recipient), channel, rawCode);
    }

    private boolean verifyOtp(String recipient, String purpose, String submittedCode, String channel) {
        Otp otp = otpRepository.findFirstByRecipientAndPurposeOrderByCreatedAtDesc(recipient, purpose);
        if (otp == null) {
            log.warn("OTP verify failed: no otp found recipient={} purpose={}", mask(recipient), purpose);
            return false;
        }


        // channel check (optional)
        if (!channel.equalsIgnoreCase(otp.getChannel())) {
            log.warn("OTP verify failed: channel mismatch recipient={} stored={} provided={}",
                    mask(recipient), otp.getChannel(), channel);
            return false;
        }

        if (Instant.now(clock).isAfter(otp.getExpiresAt())) {
            log.warn("OTP verify failed: expired recipient={}", mask(recipient));
            return false;
        }

        if (otp.getAttempts() >= maxAttempts) {
            log.warn("OTP verify failed: attempts exceeded recipient={}", mask(recipient));
            return false;
        }

        boolean ok = encoder.matches(submittedCode + pepper, otp.getCodeHash());
        otp.setAttempts(otp.getAttempts() + 1);
        otpRepository.save(otp);

        if (ok) {
            // Optionally delete/consume:
            otpRepository.delete(otp);
            log.info("OTP verify success recipient={}", mask(recipient));
        } else {
            log.warn("OTP verify failed: bad code recipient={}", mask(recipient));
        }
        return ok;
    }

    private String mask(String s) {
        if (s == null || s.isBlank()) return "***";
        int at = s.indexOf('@');
        if (at > 2) return s.substring(0, 2) + "***" + s.substring(at);
        if (s.length() > 4) return s.substring(0, 2) + "***";
        return "***";
    }
}
