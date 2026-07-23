/**
 * WASM bindings for GroupCipher operations.
 *
 * Must correspond to:
 *   - GroupCipher_EncryptMessage
 *   - GroupCipher_DecryptMessage
 */
export interface GroupCipherWasm {
  /**
   * Encrypt a group message using SenderKey.
   *
   * Corresponds to:
   *   Native.GroupCipher_EncryptMessage
   *
   * @param senderPtr Native SignalProtocolAddress pointer
   * @param distributionId UUID string identifying the group distribution
   * @param plaintext Padded plaintext bytes
   * @param senderKeyStoreHandle Native SenderKeyStore handle
   *
   * @returns Native handle to CiphertextMessage
   */
  groupCipher_encrypt_message(
    senderPtr: number,
    distributionId: string,
    plaintext: Uint8Array,
    senderKeyStoreHandle: number
  ): number;

  /**
   * Decrypt a SenderKey group message.
   *
   * Corresponds to:
   *   Native.GroupCipher_DecryptMessage
   *
   * @param senderPtr Native SignalProtocolAddress pointer
   * @param senderKeyMessageBytes Serialized SenderKeyMessage bytes
   * @param senderKeyStoreHandle Native SenderKeyStore handle
   *
   * @returns Decrypted plaintext bytes
   */
  groupCipher_decrypt_message(
    senderPtr: number,
    senderKeyMessageBytes: Uint8Array,
    senderKeyStoreHandle: number
  ): Uint8Array;
}
