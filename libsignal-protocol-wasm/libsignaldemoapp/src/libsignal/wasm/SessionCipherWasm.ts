// wasm/SessionCipherWasm.ts

import type { IdentityKeyStore } from "../state/IdentityKeyStore";
import type { SessionStore } from "../state/SessionStore";

export interface SessionCipherWasm {
  /**
   * Decrypt a PreKeySignalMessage.
   */
  sessioncipher_decrypt_prekey_signal_message(
    ciphertextHandle: number,
    remoteAddressHandle: number,
    sessionStore: unknown,
    identityKeyStore: unknown,
    preKeyStore: unknown,
    signedPreKeyStore: unknown,
    kyberPreKeyStore: unknown
  ): Promise<Uint8Array>;

  /**
   * Decrypt a SignalMessage.
   */
  sessioncipher_decrypt_signal_message(
    ciphertextHandle: number,
    remoteAddressHandle: number,
    sessionStore: unknown,
    identityKeyStore: unknown
  ): Promise<Uint8Array>;

  /**
   * Encrypt a message.
   */
  sessioncipher_encrypt_message(
    plaintext: Uint8Array,
    remoteAddressHandle: number,
    sessionStoreHandle: number,
    identityKeyStoreHandle: number,
    nowMillis: bigint
  ): Promise<number>; // returns CiphertextMessage handle
}