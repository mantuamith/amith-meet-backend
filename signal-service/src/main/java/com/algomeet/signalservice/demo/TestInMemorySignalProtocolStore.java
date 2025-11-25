//
// Copyright 2023 Signal Messenger, LLC.
// SPDX-License-Identifier: AGPL-3.0-only
//

package com.algomeet.signalservice.demo;

import org.signal.libsignal.protocol.IdentityKey;
import org.signal.libsignal.protocol.IdentityKeyPair;
import org.signal.libsignal.protocol.ecc.ECKeyPair;
import org.signal.libsignal.protocol.state.impl.InMemorySignalProtocolStore;
import org.signal.libsignal.protocol.util.KeyHelper;

public class TestInMemorySignalProtocolStore extends InMemorySignalProtocolStore {
  public TestInMemorySignalProtocolStore() {
    super(generateIdentityKeyPair(), generateRegistrationId());
  }

  private static IdentityKeyPair generateIdentityKeyPair() {
    ECKeyPair identityKeyPairKeys = ECKeyPair.generate();

    return new IdentityKeyPair(
        new IdentityKey(identityKeyPairKeys.getPublicKey()), identityKeyPairKeys.getPrivateKey());
  }

  private static int generateRegistrationId() {
    return KeyHelper.generateRegistrationId(false);
  }
}
