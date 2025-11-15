package com.algomeet.opaqueservice.service;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.algomeet.opaqueservice.dto.UserRecord;

@Service
public class UserStore {
    private final ConcurrentHashMap<String, UserRecord> map = new ConcurrentHashMap<>();

    public void save(UserRecord r){ map.put(r.username(), r); }
    public UserRecord get(String username){ return map.get(username); }
}
