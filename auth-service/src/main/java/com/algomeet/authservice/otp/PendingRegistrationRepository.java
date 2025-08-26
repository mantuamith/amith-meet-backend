package com.algomeet.authservice.otp;

import com.algomeet.authservice.dto.PendingRegistrationDoc;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PendingRegistrationRepository
        extends MongoRepository<PendingRegistrationDoc, String> {



}
