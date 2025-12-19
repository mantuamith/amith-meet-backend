import { InvalidKeyException } from "../exceptions/InvalidKeyException";

import type { SessionStore } from "../state/SessionStore";
import type { PreKeyStore } from "../state/PreKeyStore";
import type { SignedPreKeyStore } from "../state/SignedPreKeyStore";
import type { IdentityKeyStore } from "../state/IdentityKeyStore";
import type { SignalProtocolStore } from "../state/SignalProtocolStore";


//import { sessionBuilder as sessionBuilderWasm } from "libsignal_wasm_pqxdh";
import type { SignalProtocolAddress } from "./SignalProtocolAddress";
import { UntrustedIdentityException } from "../exceptions/UntrustedIdentityException";
import type { PreKeyBundle } from "../state/PreKeyBundle";
import { sessionBuilder as  sessionBuilderWasm } from "../../../../libsignal-protocol-wasm/wasm-wrapper/pkg/libsignal_wasm_pqxdh";

/**
 * TypeScript equivalent of Java's SessionBuilder.
 * Responsible for creating a new session using a PreKeyBundle.
 */
export class SessionBuilder {
  private readonly sessionStore: SessionStore;
  private readonly preKeyStore: PreKeyStore;
  private readonly signedPreKeyStore: SignedPreKeyStore;
  private readonly identityKeyStore: IdentityKeyStore;
  private readonly remoteAddress: SignalProtocolAddress;

  constructor(
    sessionStore: SessionStore,
    preKeyStore: PreKeyStore,
    signedPreKeyStore: SignedPreKeyStore,
    identityKeyStore: IdentityKeyStore,
    remoteAddress: SignalProtocolAddress
  ) {
    this.sessionStore = sessionStore;
    this.preKeyStore = preKeyStore;
    this.signedPreKeyStore = signedPreKeyStore;
    this.identityKeyStore = identityKeyStore;
    this.remoteAddress = remoteAddress;
  }

  /**
   * Convenience constructor: one store implements all required interfaces.
   */
  static fromStore(
    store: SignalProtocolStore,
    remoteAddress: SignalProtocolAddress
  ): SessionBuilder {
    return new SessionBuilder(store, store, store, store, remoteAddress);
  }

  // --------------------------------------------------------------------
  // Java: process(preKey)
  // --------------------------------------------------------------------

  process(preKey: PreKeyBundle): void {
    this.processWithTime(preKey, Date.now());
  }

  // --------------------------------------------------------------------
  // Java: process(preKey, Instant.now())
  // --------------------------------------------------------------------

  processWithTime(preKey: PreKeyBundle, nowMs: number): void {
    try {
      // WASM expects:
      //   preKey.handle
      //   remoteAddress.handle
      //   sessionStore object
      //   identityKeyStore object
      //   timestamp (ms)
      console.log("identityStoreHandle", this.identityKeyStore.getIdentityKeyStoreHandle());
      console.log("sessionStoreHandle", this.sessionStore.getSessionStoreHandle());

      sessionBuilderWasm.sessionbuilder_process_prekey_bundle(
        preKey.handle,
        this.remoteAddress.handle,
        this.sessionStore.getSessionStoreHandle(),
        this.identityKeyStore.getIdentityKeyStoreHandle(),
        BigInt(nowMs)
      );
    } catch (err: any) {
      // Java version wraps errors via filterExceptions
      if (err instanceof InvalidKeyException) {
        throw err;
      }
      if (err instanceof UntrustedIdentityException) {
        throw err;
      }

      throw new Error(`SessionBuilder.process failed: ${err?.message || err}`);
    }
  }
}
