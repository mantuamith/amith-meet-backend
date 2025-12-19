
import { InvalidKeyException } from "../../exceptions/InvalidKeyException";
import { kemSecretKey as  kemSecretKeyWasm } from "../../../../../libsignal-protocol-wasm/wasm-wrapper/pkg/libsignal_wasm_pqxdh";


/**
 * TypeScript equivalent of org.signal.libsignal.protocol.kem.KEMSecretKey
 *
 * Wraps a WASM handle pointing to a Kyber secret key.
 */
export class KEMSecretKey {
  /** Native WASM pointer (handle) */
  readonly handle: number;

  // ---------------------------------------------------------
  // Constructors
  // ---------------------------------------------------------

  /**
   * new KEMSecretKey(handle: number) – internal use
   */
  constructor(handle: number);

  /**
   * new KEMSecretKey(bytes: Uint8Array) – deserialize from bytes
   */
  constructor(bytes: Uint8Array);

  constructor(arg: number | Uint8Array) {
    if (typeof arg === "number") {
      if (arg === 0) {
        throw new InvalidKeyException("Null WASM handle for KEMSecretKey");
      }
      this.handle = arg;
      return;
    }

    // Deserialize from bytes
    if (arg instanceof Uint8Array) {
      const ptr = kemSecretKeyWasm.kybersecretkey_deserialize(arg);
      if (!ptr) {
        throw new InvalidKeyException("Failed to deserialize KyberSecretKey");
      }
      this.handle = ptr;
      return;
    }

    throw new InvalidKeyException("Invalid KEMSecretKey constructor argument");
  }

  // ---------------------------------------------------------
  // Serialization
  // ---------------------------------------------------------

  /**
   * Serialize this Kyber secret key into compact binary form.
   */
  serialize(): Uint8Array {
    return kemSecretKeyWasm.kybersecretkey_serialize(this.handle);
  }

  // ---------------------------------------------------------
  // Cleanup
  // ---------------------------------------------------------

  /**
   * Explicitly free the WASM-side key.
   * (Matches NativeHandleGuard.release in Java.)
   */
  destroy(): void {
    kemSecretKeyWasm.kybersecretkey_destroy(this.handle);
  }
}
