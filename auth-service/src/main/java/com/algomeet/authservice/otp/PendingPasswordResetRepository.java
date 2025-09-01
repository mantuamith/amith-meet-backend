// PendingPasswordResetRepository.java
package com.algomeet.authservice.otp;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface PendingPasswordResetRepository
    extends MongoRepository<PendingPasswordResetDoc, String> {}
