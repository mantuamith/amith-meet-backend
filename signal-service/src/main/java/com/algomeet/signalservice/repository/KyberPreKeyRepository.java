package com.algomeet.signalservice.repository;


import org.springframework.data.jpa.repository.JpaRepository;

import com.algomeet.signalservice.entity.KyberPreKey;
import com.algomeet.signalservice.entity.KyberPreKeyId;


public interface KyberPreKeyRepository extends JpaRepository<KyberPreKey, KyberPreKeyId> {
}
