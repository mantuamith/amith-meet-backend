export interface SessionRecordWasm {
  /**
   * Create a new, empty SessionRecord.
   * Returns a native pointer.
   */
  sessionrecord_new_fresh(): number;

  /**
   * Destroy a SessionRecord (free native memory).
   */
  sessionrecord_destroy(ptr: number): void;

  /**
   * Deserialize a SessionRecord from bytes.
   * Returns a native handle, or 0 on failure.
   */
  sessionrecord_deserialize(bytes: Uint8Array): number;

  /**
   * Move current session state → archive, and replace with a fresh state.
   */
  sessionrecord_archive_current_state(ptr: number): void;

  /**
   * Get the current session's negotiated version.
   */
  sessionrecord_get_session_version(ptr: number): number;

  /**
   * Get the remote registration ID stored inside the session.
   */
  sessionrecord_get_remote_registration_id(ptr: number): number;

  /**
   * Get the local registration ID stored inside the session.
   */
  sessionrecord_get_local_registration_id(ptr: number): number;

  /**
   * Get remote identity key as a serialized EC public key.
   * Returns Uint8Array or null (if no key available).
   */
  sessionrecord_get_remote_identity_key_public(ptr: number): Uint8Array | null;

  /**
   * Get local identity key as a serialized EC public key.
   */
  sessionrecord_get_local_identity_key_public(ptr: number): Uint8Array;

  /**
   * Whether this session has a valid sender chain.
   * nowMs is milliseconds since epoch (Date.now()).
   */
  sessionrecord_has_usable_sender_chain(ptr: number, nowMs: number): boolean;

  /**
   * Whether the current ratchet key matches the given EC public key.
   */
  sessionrecord_current_ratchet_key_matches(ptr: number, keyPtr: number): boolean;

  /**
   * Serialize the SessionRecord to raw bytes.
   */
  sessionrecord_serialize(ptr: number): Uint8Array;
}
