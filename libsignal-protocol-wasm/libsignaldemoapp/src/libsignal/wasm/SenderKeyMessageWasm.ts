export interface SenderKeyMessageWasm {
  /**
   * Deserialize a SenderKeyMessage from bytes.
   *
   * Throws (via wasm):
   *  - InvalidMessageException
   *  - InvalidVersionException
   *  - LegacyMessageException
   */
  senderkeymessage_deserialize(serialized: Uint8Array): number;

  /**
   * Destroy a SenderKeyMessage handle.
   */
  senderkeymessage_destroy(handle: number): void;

  /**
   * Get distribution UUID (returned as string).
   */
  senderkeymessage_get_distribution_id(handle: number): string;

  /**
   * Get sender chain ID.
   */
  senderkeymessage_get_chain_id(handle: number): number;

  /**
   * Get message iteration.
   */
  senderkeymessage_get_iteration(handle: number): number;

  /**
   * Get ciphertext bytes.
   */
  senderkeymessage_get_ciphertext(handle: number): Uint8Array;

  /**
   * Verify the message signature.
   *
   * @param handle SenderKeyMessage handle
   * @param publicKeyHandle ECPublicKey handle
   * @returns true if valid
   *
   * Throws (via wasm):
   *  - InvalidMessageException
   */
  senderkeymessage_verify_signature(
    handle: number,
    publicKeyHandle: number
  ): boolean;

  /**
   * Get serialized form.
   */
  senderkeymessage_get_serialized(handle: number): Uint8Array;
}
