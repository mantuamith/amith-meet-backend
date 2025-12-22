/**
 * WASM bindings for PreKeySignalMessage.
 *
 * This mirrors Native.PreKeySignalMessage_* in Java.
 */
export interface PreKeySignalMessageWasm {
  // --------------------------------------------------
  // Lifecycle
  // --------------------------------------------------

  /** Deserialize from wire format → native handle */
  prekeysignalmessage_deserialize(serialized: Uint8Array): number;

  /** Destroy native object */
  prekeysignalmessage_destroy(handle: number): void;

  // --------------------------------------------------
  // Accessors
  // --------------------------------------------------

  prekeysignalmessage_get_version(handle: number): number;

  prekeysignalmessage_get_identity_key(handle: number): number;

  prekeysignalmessage_get_registration_id(handle: number): number;

  /**
   * Returns:
   *  - preKeyId >= 0
   *  - or -1 if absent
   */
  prekeysignalmessage_get_pre_key_id(handle: number): number;

  prekeysignalmessage_get_signed_pre_key_id(handle: number): number;

  prekeysignalmessage_get_base_key(handle: number): number;

  prekeysignalmessage_get_signal_message(handle: number): number;

  // --------------------------------------------------
  // Serialization
  // --------------------------------------------------

  prekeysignalmessage_serialize(handle: number): Uint8Array;
}
