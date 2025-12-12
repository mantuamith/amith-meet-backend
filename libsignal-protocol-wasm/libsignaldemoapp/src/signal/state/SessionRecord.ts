import { sessionRecord as sessionRecordWasm } from "libsignal_wasm_pqxdh";
import { IdentityKey } from "../protocol/IdentityKey";
import { InvalidMessageException } from "../exceptions/InvalidMessageException";
import { ECPublicKey } from "../protocol/ecc/ECPublicKey";


/**
 * TypeScript equivalent of libsignal's SessionRecord.
 *
 * Wraps a native WASM pointer and exposes methods matching the Java API.
 */
export class SessionRecord {
  readonly handle: number;

  /**
   * Construct a new fresh SessionRecord.
   */
  constructor();
  /**
   * Construct from serialized bytes.
   */
  constructor(serialized: Uint8Array);
  /**
   * Internal: construct from raw native pointer.
   */
  constructor(nativeHandle: number);

  constructor(arg?: any) {
    // Case 1: no args → new fresh record
    if (arg === undefined) {
      this.handle = sessionRecordWasm.sessionrecord_new_fresh();
      return;
    }

    // Case 2: serialized bytes
    if (arg instanceof Uint8Array) {
      const ptr = sessionRecordWasm.sessionrecord_deserialize(arg);
      if (ptr === 0) {
        throw new InvalidMessageException("Failed to deserialize SessionRecord");
      }
      this.handle = ptr;
      return;
    }

    // Case 3: explicit native handle
    if (typeof arg === "number") {
      this.handle = arg;
      return;
    }

    throw new Error("Invalid SessionRecord constructor argument");
  }

  // ---------------------------------------------------------
  // Destructor
  // ---------------------------------------------------------

  destroy(): void {
    sessionRecordWasm.sessionrecord_destroy(this.handle);
  }

  // ---------------------------------------------------------
  // Methods
  // ---------------------------------------------------------

  archiveCurrentState(): void {
    sessionRecordWasm.sessionrecord_archive_current_state(this.handle);
  }

  getSessionVersion(): number {
    return sessionRecordWasm.sessionrecord_get_session_version(this.handle);
  }

  getRemoteRegistrationId(): number {
    return sessionRecordWasm.sessionrecord_get_remote_registration_id(this.handle);
  }

  getLocalRegistrationId(): number {
    return sessionRecordWasm.sessionrecord_get_local_registration_id(this.handle);
  }

  getRemoteIdentityKey(): IdentityKey | null {
    const bytes =
      sessionRecordWasm.sessionrecord_get_remote_identity_key_public(this.handle);

    if (!bytes || bytes.length === 0) {
      return null;
    }
    return new IdentityKey(bytes);
  }

  getLocalIdentityKey(): IdentityKey {
    const bytes =
      sessionRecordWasm.sessionrecord_get_local_identity_key_public(this.handle);

    return new IdentityKey(bytes);
  }

  hasSenderChain(now: Date = new Date()): boolean {
    return sessionRecordWasm.sessionrecord_has_usable_sender_chain(
      this.handle,
      BigInt(now.getTime())
    );
  }

  currentRatchetKeyMatches(key: ECPublicKey): boolean {
    return sessionRecordWasm.sessionrecord_current_ratchet_key_matches(
      this.handle,
      key.handle
    );
  }

  serialize(): Uint8Array {
    return sessionRecordWasm.sessionrecord_serialize(this.handle);
  }
}
