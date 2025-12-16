import type { SessionStore } from "../state/SessionStore";
import type { PreKeyStore } from "../state/PreKeyStore";
import type { SignedPreKeyStore } from "../state/SignedPreKeyStore";
import type { KyberPreKeyStore } from "../state/KyberPreKeyStore";
import type { IdentityKeyStore } from "../state/IdentityKeyStore";
import type { SignalProtocolStore } from "../state/SignalProtocolStore";
import type { SignalProtocolAddress } from "./SignalProtocolAddress";
import { SessionRecord } from "../state/SessionRecord";

import {
  sessionCipher as sessionCipherWasm,
} from "libsignal_wasm_pqxdh";
import type { CiphertextMessage } from "./message/CiphertextMessage";
import type { PreKeySignalMessage } from "./message/PreKeySignalMessage";
import type { SignalMessage } from "./message/SignalMessage";

/**
 * TypeScript equivalent of Java's SessionCipher.
 *
 * Main entry point for encrypt/decrypt operations once a session exists.
 *
 * NOTE:
 * - Not thread-safe (same as Java)
 * - Async because WASM + JS stores
 */
export class SessionCipher {
  private readonly sessionStore: SessionStore;
  private readonly preKeyStore: PreKeyStore;
  private readonly signedPreKeyStore: SignedPreKeyStore;
  private readonly kyberPreKeyStore: KyberPreKeyStore;
  private readonly identityKeyStore: IdentityKeyStore;
  private readonly remoteAddress: SignalProtocolAddress;

  // ------------------------------------------------------------------
  // Constructors
  // ------------------------------------------------------------------

  constructor(
    sessionStore: SessionStore,
    preKeyStore: PreKeyStore,
    signedPreKeyStore: SignedPreKeyStore,
    kyberPreKeyStore: KyberPreKeyStore,
    identityKeyStore: IdentityKeyStore,
    remoteAddress: SignalProtocolAddress
  ) {
    this.sessionStore = sessionStore;
    this.preKeyStore = preKeyStore;
    this.signedPreKeyStore = signedPreKeyStore;
    this.kyberPreKeyStore = kyberPreKeyStore;
    this.identityKeyStore = identityKeyStore;
    this.remoteAddress = remoteAddress;
  }

  /**
   * Convenience constructor: single store implements all interfaces
   * (matches Java SignalProtocolStore constructor)
   */
  static fromStore(
    store: SignalProtocolStore,
    remoteAddress: SignalProtocolAddress
  ): SessionCipher {
    return new SessionCipher(
      store,
      store,
      store,
      store,
      store,
      remoteAddress
    );
  }

  // ------------------------------------------------------------------
  // Encrypt
  // ------------------------------------------------------------------

  /**
   * Encrypt a message using current time.
   */
  async encrypt(paddedMessage: Uint8Array): Promise<CiphertextMessage> {
    return this.encryptWithTime(paddedMessage, Date.now());
  }

  /**
   * Encrypt a message with explicit timestamp (used for testing).
   */
  async encryptWithTime(
    paddedMessage: Uint8Array,
    nowMs: number
  ): Promise<CiphertextMessage> {
    return sessionCipherWasm.sessioncipher_encrypt(
      paddedMessage,
      this.remoteAddress.handle,
      this.sessionStore,
      this.identityKeyStore,
      BigInt(nowMs)
    );
  }

  // ------------------------------------------------------------------
  // Decrypt
  // ------------------------------------------------------------------

  /**
   * Decrypt a PreKeySignalMessage.
   */
  async decryptPreKeySignalMessage(
    ciphertext: PreKeySignalMessage
  ): Promise<Uint8Array> {
    return sessionCipherWasm.sessioncipher_decrypt_prekey_signal_message(
      ciphertext.handle,
      this.remoteAddress.handle,
      this.sessionStore,
      this.identityKeyStore,
      this.preKeyStore,
      this.signedPreKeyStore,
      this.kyberPreKeyStore
    );
  }

  /**
   * Decrypt a SignalMessage.
   */
  async decryptSignalMessage(
    ciphertext: SignalMessage
  ): Promise<Uint8Array> {
    return sessionCipherWasm.sessioncipher_decrypt_signal_message(
      ciphertext.handle,
      this.remoteAddress.handle,
      this.sessionStore,
      this.identityKeyStore
    );
  }

  // ------------------------------------------------------------------
  // Session metadata
  // ------------------------------------------------------------------

  /**
   * Get the remote party's registration ID.
   */
  async getRemoteRegistrationId(): Promise<number> {
    if (!this.sessionStore.containsSession(this.remoteAddress)) {
      throw new Error(`No session for (${this.remoteAddress.toString()})`);
    }

    const record = await this.sessionStore.loadSession(this.remoteAddress);
    if (!record) {
      throw new Error(`No session for (${this.remoteAddress.toString()})`);
    }

    return record.getRemoteRegistrationId();
  }

  /**
   * Get the current session version.
   */
  async getSessionVersion(): Promise<number> {
    if (!this.sessionStore.containsSession(this.remoteAddress)) {
      throw new Error(`No session for (${this.remoteAddress.toString()})`);
    }

    const record = await this.sessionStore.loadSession(this.remoteAddress);
    if (!record) {
      throw new Error(`No session for (${this.remoteAddress.toString()})`);
    }

    return record.getSessionVersion();
  }
}
