package com.algomeet.authservice.otp;

import com.algomeet.authservice.enums.OtpPurpose;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface OtpRepository extends MongoRepository<Otp, String> {
    Optional<Otp> findTopByRecipientAndPurposeOrderByCreatedAtDesc(String recipient, OtpPurpose purpose);

    Otp findFirstByRecipientAndPurposeOrderByCreatedAtDesc(String recipient, String purpose);
}
