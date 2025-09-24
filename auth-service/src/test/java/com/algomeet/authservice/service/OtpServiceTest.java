package com.algomeet.authservice.service;

import com.algomeet.authservice.config.AuthProperties;
import com.algomeet.authservice.exception.*;
import com.algomeet.authservice.notify.EmailSender;
import com.algomeet.authservice.otp.Otp;
import com.algomeet.authservice.otp.OtpRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OtpServiceTest {

    private AuthProperties props;
    private OtpRepository otpRepository;
    private Clock clock;
    private EmailSender emailSender;
    private OtpService service;

    @BeforeEach
    void setup() {
        props = mock(AuthProperties.class);
        var otpProps = new AuthProperties.Otp();
        otpProps.setTtlSeconds(300); // 5 minutes
        when(props.getOtp()).thenReturn(otpProps);

        otpRepository = mock(OtpRepository.class);
        clock = Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC);
        emailSender = mock(EmailSender.class);

        service = new OtpService(props, otpRepository, clock, emailSender);
    }

    @Test
    void initEmailRegistrationOtp_persists_and_emails_code_then_verify_succeeds() {
        String email = "alice@example.com";

        // 1) INIT: send code
        service.initEmailRegistrationOtp(email);
        // capture saved OTP
        ArgumentCaptor<Otp> savedCap = ArgumentCaptor.forClass(Otp.class);
        verify(otpRepository).save(savedCap.capture());
        Otp saved = savedCap.getValue();
        assertThat(saved.getRecipient()).isEqualTo(email);
        assertThat(saved.getPurpose()).isEqualTo("REGISTER");
        assertThat(saved.getChannel()).isEqualTo("EMAIL");
        assertThat(saved.getCreatedAt()).isEqualTo(Instant.parse("2025-01-01T00:00:00Z"));
        assertThat(saved.getExpiresAt()).isAfter(saved.getCreatedAt());
        assertThat(saved.getCodeHash()).isNotBlank();

        // capture email content => extract the raw 6-digit code
        ArgumentCaptor<String> toCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> subjectCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> htmlCap = ArgumentCaptor.forClass(String.class);
        verify(emailSender).send(toCap.capture(), subjectCap.capture(), htmlCap.capture());

        assertThat(toCap.getValue()).isEqualTo(email);
        assertThat(subjectCap.getValue()).contains("OTP");

        String html = htmlCap.getValue();
        String code = extractSixDigitCode(html);
        assertThat(code).hasSize(6).matches("\\d{6}");

        // 2) VERIFY: mock repo lookup returns the saved doc we just captured
        when(otpRepository.findFirstByRecipientAndPurposeOrderByCreatedAtDesc(email, "REGISTER"))
                .thenReturn(saved);

        // repo will be called to update attempts and then delete on success
        // we already have a stub for save(saved) from earlier; allow another save
        // delete should be called after a successful verify
        boolean ok = service.verifyEmailRegistrationOtp(email, code);
        assertThat(ok).isTrue();

        verify(otpRepository, atLeastOnce()).save(any(Otp.class));
        verify(otpRepository).delete(saved);
    }

    @Test
    void verifyEmailRegistrationOtp_fails_when_no_otp_found() {
        String email = "missing@example.com";
        when(otpRepository.findFirstByRecipientAndPurposeOrderByCreatedAtDesc(email, "REGISTER"))
                .thenReturn(null);

        Assertions.assertThrows(OtpNotFoundException.class, () ->
                service.verifyEmailRegistrationOtp(email, "123456"));
    }

    @Test
    void verifyEmailRegistrationOtp_fails_on_channel_mismatch() {
        String email = "alice@example.com";
        Otp saved = Otp.builder()
                .recipient(email)
                .purpose("REGISTER")
                .channel("PHONE") // <-- mismatch (expect EMAIL)
                .codeHash("dummy")
                .attempts(0)
                .createdAt(Instant.now(clock))
                .expiresAt(Instant.now(clock).plusSeconds(300))
                .build();

        when(otpRepository.findFirstByRecipientAndPurposeOrderByCreatedAtDesc(email, "REGISTER"))
                .thenReturn(saved);

        Assertions.assertThrows(OtpChannelMismatchException.class, () ->
                service.verifyEmailRegistrationOtp(email, "123456"));
    }

    @Test
    void verifyEmailRegistrationOtp_fails_when_expired() {
        String email = "alice@example.com";
        Otp saved = Otp.builder()
                .recipient(email)
                .purpose("REGISTER")
                .channel("EMAIL")
                .codeHash("dummy")
                .attempts(0)
                .createdAt(Instant.now(clock).minusSeconds(600))
                .expiresAt(Instant.now(clock).minusSeconds(1)) // already expired
                .build();

        when(otpRepository.findFirstByRecipientAndPurposeOrderByCreatedAtDesc(email, "REGISTER"))
                .thenReturn(saved);

        Assertions.assertThrows(OtpExpiredException.class, () ->
                service.verifyEmailRegistrationOtp(email, "123456"));
    }

    @Test
    void verifyEmailRegistrationOtp_fails_when_attempts_exceeded() {
        String email = "alice@example.com";
        Otp saved = Otp.builder()
                .recipient(email)
                .purpose("REGISTER")
                .channel("EMAIL")
                .codeHash("dummy")
                .attempts(5) // >= maxAttempts (5)
                .createdAt(Instant.now(clock))
                .expiresAt(Instant.now(clock).plusSeconds(300))
                .build();

        when(otpRepository.findFirstByRecipientAndPurposeOrderByCreatedAtDesc(email, "REGISTER"))
                .thenReturn(saved);

        Assertions.assertThrows(OtpAttemptsExceededException.class, () ->
                service.verifyEmailRegistrationOtp(email, "123456"));
    }

    @Test
    void verifyEmailRegistrationOtp_fails_when_code_incorrect_and_does_not_delete() {
        String email = "alice@example.com";

        // Prepare a stored OTP hashed with the same pepper as service uses
        var encoder = new BCryptPasswordEncoder();
        String correctCode = "654321";
        String hashed = encoder.encode(correctCode + "CHANGE_ME_ADD_SECRET_PEPPER");

        Otp saved = Otp.builder()
                .recipient(email)
                .purpose("REGISTER")
                .channel("EMAIL")
                .codeHash(hashed)
                .attempts(0)
                .createdAt(Instant.now(clock))
                .expiresAt(Instant.now(clock).plusSeconds(300))
                .build();

        when(otpRepository.findFirstByRecipientAndPurposeOrderByCreatedAtDesc(email, "REGISTER"))
                .thenReturn(saved);

        Assertions.assertThrows(OtpInvalidCodeException.class, () ->
                service.verifyEmailRegistrationOtp(email, "111111")); // wrong code

        // attempts should be incremented and saved, but NOT deleted
        verify(otpRepository, atLeastOnce()).save(any(Otp.class));
        verify(otpRepository, never()).delete(saved);
    }

    private static String extractSixDigitCode(String html) {
        Pattern p = Pattern.compile("(?:^|[^\\d])(\\d{6})(?:[^\\d]|$)");
        Matcher m = p.matcher(html);
        if (m.find()) return m.group(1);
        throw new AssertionError("No 6-digit OTP code found in email HTML: " + html);
        }
}
