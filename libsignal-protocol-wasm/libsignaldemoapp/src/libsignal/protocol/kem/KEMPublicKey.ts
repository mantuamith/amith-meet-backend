import { InvalidKeyException } from "../../exceptions/InvalidKeyException";
import { kemPublicKey as  kemPublicKeyWasm } from "libsignal_wasm_pqxdh";

/**
 * TypeScript equivalent of Java's KEMPublicKey.
 */
export class KEMPublicKey {
  /** Native pointer managed by WASM */
  public readonly handle: number;

  // --------------------------------------------------------------------
  // Constructors
  // --------------------------------------------------------------------

  /** new KEMPublicKey(bytes, offset) → deprecated in Java but supported */
  constructor(serialized: Uint8Array, offset: number);
  /** new KEMPublicKey(bytes, offset, length) */
  constructor(serialized: Uint8Array, offset: number, length: number);
  /** new KEMPublicKey(bytes) */
  constructor(serialized: Uint8Array);
  /** new KEMPublicKey(nativeHandle) */
  constructor(nativeHandle: number);

  constructor(arg1: Uint8Array | number, arg2?: number, arg3?: number) {
    // Case 1 — from existing handle
    if (typeof arg1 === "number") {
      if (arg1 === 0) {
        throw new InvalidKeyException("Null native handle for KEMPublicKey");
      }
      this.handle = arg1;
      return;
    }

    // Case 2 — new KEMPublicKey(bytes)
    if (arg1 instanceof Uint8Array && arg2 === undefined) {
      const bytes = arg1;
      this.handle = kemPublicKeyWasm.kyberpublickey_deserialize(bytes, 0, bytes.length);
      if (this.handle === 0) throw new InvalidKeyException("Failed to deserialize KEM public key");
      return;
    }

    // Case 3 — new KEMPublicKey(bytes, offset)
    if (arg1 instanceof Uint8Array && typeof arg2 === "number" && arg3 === undefined) {
      const bytes = arg1;
      const offset = arg2;
      const length = bytes.length - offset;
      this.handle = kemPublicKeyWasm.kyberpublickey_deserialize(bytes, offset, length);
      if (this.handle === 0) throw new InvalidKeyException("Failed to deserialize KEM public key");
      return;
    }

    // Case 4 — new KEMPublicKey(bytes, offset, length)
    if (
      arg1 instanceof Uint8Array &&
      typeof arg2 === "number" &&
      typeof arg3 === "number"
    ) {
      const bytes = arg1;
      const offset = arg2;
      const length = arg3;
      this.handle = kemPublicKeyWasm.kyberpublickey_deserialize(bytes, offset, length);
      if (this.handle === 0) throw new InvalidKeyException("Failed to deserialize KEM public key");
      return;
    }

    throw new InvalidKeyException("Invalid KEMPublicKey constructor arguments");
  }

  // --------------------------------------------------------------------
  // API: serialize()
  // --------------------------------------------------------------------

  serialize(): Uint8Array {
    return kemPublicKeyWasm.kyberpublickey_serialize(this.handle);
  }

  // --------------------------------------------------------------------
  // equals() / hashCode()
  // --------------------------------------------------------------------

  equals(other: unknown): boolean {
    if (!(other instanceof KEMPublicKey)) return false;
    return kemPublicKeyWasm.kyberpublickey_equals(this.handle, other.handle);
  }

  hashCode(): number {
    const bytes = this.serialize();
    let hash = 0;
    for (const b of bytes) {
      hash = ((hash << 5) - hash) ^ b;
    }
    return hash | 0;
  }

  // --------------------------------------------------------------------
  // Manual cleanup
  // --------------------------------------------------------------------

  destroy(): void {
    kemPublicKeyWasm.kyberpublickey_destroy(this.handle);
  }
}
