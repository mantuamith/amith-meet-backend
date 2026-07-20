/**
 * TypeScript equivalent of Java's CiphertextMessage interface
 * (instance-side only)
 */
export interface CiphertextMessage {
  /** Native WASM handle */
  readonly handle: number;

  /** Serialize ciphertext into wire format */
  serialize(): Uint8Array;

  /** Message type (WHISPER, PREKEY, etc.) */
  getType(): number;
}

// CiphertextMessage interface (constants must be static)
export interface CiphertextMessage {
  serialize(): Uint8Array;
  getType(): number;
}

export const CiphertextMessageConstants = {
  CURRENT_VERSION: 3,
  WHISPER_TYPE: 2,
  PREKEY_TYPE: 3,
  SENDERKEY_TYPE: 7,
  PLAINTEXT_CONTENT_TYPE: 8,
  ENCRYPTED_MESSAGE_OVERHEAD: 53,
} as const;
