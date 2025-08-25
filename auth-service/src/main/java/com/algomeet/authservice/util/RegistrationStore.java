package com.algomeet.authservice.util;

import com.algomeet.authservice.config.AuthProperties;
import com.algomeet.authservice.dto.PendingRegistration;
import com.algomeet.authservice.dto.PendingRegistrationDoc;
import com.algomeet.authservice.otp.PendingRegistrationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RegistrationStore {
    private final PendingRegistrationRepository repo;
    private final AuthProperties props;           // otp.ttlSeconds
    private final PasswordEncoder passwordEncoder; // BCryptPasswordEncoder bean

    public String save(PendingRegistration pr) {
        String txn = UUID.randomUUID().toString();
        var now = Instant.now();
        var doc = new PendingRegistrationDoc(
                txn,
                pr.getUsername(),
                pr.getEmail(),
                pr.getPhone(),
                passwordEncoder.encode(pr.getPassword()), // hash once, never store raw
                pr.getDeviceId(),
                pr.getDeviceType(),
                now,
                now.plusSeconds(props.getOtp().getTtlSeconds()) // TTL
        );
        repo.save(doc);
        return txn;
    }

    // Return the password **hash** (not raw) so verify step can create the user
    public PendingRegistration get(String txn) {
        return repo.findById(txn).map(d -> new PendingRegistration(
                        d.getUsername(), d.getEmail(), d.getPhone(),
                        d.getPasswordHash(),
                        d.getDeviceId(), d.getDeviceType(), d.getCreatedAt()
                )
        ).orElse(null);
    }

    public void delete(String txn) { repo.deleteById(txn); }
}
