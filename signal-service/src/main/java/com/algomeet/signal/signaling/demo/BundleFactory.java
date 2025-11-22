//
// Copyright 2023 Signal Messenger, LLC.
// SPDX-License-Identifier: AGPL-3.0-only
//

package com.algomeet.signal.signaling.demo;

import org.signal.libsignal.protocol.InvalidKeyException;
import org.signal.libsignal.protocol.state.PreKeyBundle;
import org.signal.libsignal.protocol.state.SignalProtocolStore;

public interface BundleFactory {
  PreKeyBundle createBundle(SignalProtocolStore store) throws InvalidKeyException;
}
