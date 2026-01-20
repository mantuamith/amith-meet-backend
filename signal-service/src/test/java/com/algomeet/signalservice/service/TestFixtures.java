package com.algomeet.signalservice.service;

import java.util.List;

import com.algomeet.signalservice.dto.DevicePreKeyBundleRequest;
import com.algomeet.signalservice.dto.KyberPreKeyRequest;
import com.algomeet.signalservice.dto.OneTimePreKeyRequest;
import com.algomeet.signalservice.dto.SignedPreKeyRequest;

class TestFixtures {

    static DevicePreKeyBundleRequest devicePreKeyBundleRequest() {
        DevicePreKeyBundleRequest req = new DevicePreKeyBundleRequest();

        SignedPreKeyRequest spk = new SignedPreKeyRequest();
        KyberPreKeyRequest kpk = new KyberPreKeyRequest();
        OneTimePreKeyRequest otk = new OneTimePreKeyRequest();

        req.setSignedPreKey(spk); // mapper tested elsewhere
        req.setKyberPreKey(kpk);
        req.setOneTimePreKeys(List.of(otk));

        return req;
    }
}
