import type { CiphertextMessage } from "./CiphertextMessage";

/**
 * Shared base class for all ciphertext message types.
 * Mirrors NativeHandleGuard.Owner in Java.
 */
export abstract class BaseCiphertextMessage implements CiphertextMessage {
  // --------------------------------------------------
  // Static constants (Java static final)
  // --------------------------------------------------

  static readonly CURRENT_VERSION = 3;

  static readonly WHISPER_TYPE = 2;
  static readonly PREKEY_TYPE = 3;
  static readonly SENDERKEY_TYPE = 7;
  static readonly PLAINTEXT_CONTENT_TYPE = 8;

  static readonly ENCRYPTED_MESSAGE_OVERHEAD = 53;

  // --------------------------------------------------
  // Instance
  // --------------------------------------------------

  public readonly handle: number;

  protected constructor(handle: number) {
    if (!handle) {
      throw new Error("Invalid native CiphertextMessage handle");
    }
    this.handle = handle;
  }

  // --------------------------------------------------
  // Abstract API
  // --------------------------------------------------

  abstract serialize(): Uint8Array;
  abstract getType(): number;
}
