import { InvalidMessageException } from "../exceptions/InvalidMessageException";
import type { SenderKeyRecordWasm } from "../wasm/SenderKeyRecordWasm";

declare const wasm: SenderKeyRecordWasm;

export class SenderKeyRecord {
  public readonly handle: number;

  // --------------------------------------------------------------
  // Constructors
  // --------------------------------------------------------------

  /**
   * new SenderKeyRecord(nativeHandle)
   */
  constructor(nativeHandle: number);

  /**
   * new SenderKeyRecord(serializedBytes)
   */
  constructor(serialized: Uint8Array);

  constructor(arg: any) {
    // Case 1: Construct from native handle
    if (typeof arg === "number") {
      if (!arg) {
        throw new Error("Null native handle for SenderKeyRecord");
      }
      this.handle = arg;
      return;
    }

    // Case 2: Construct from serialized bytes
    if (arg instanceof Uint8Array) {
      try {
        this.handle = wasm.senderkeyrecord_deserialize(arg);
      } catch (_) {
        throw new InvalidMessageException("Failed to deserialize SenderKeyRecord");
      }
      return;
    }

    throw new Error("Invalid SenderKeyRecord constructor arguments");
  }

  // --------------------------------------------------------------
  // Methods
  // --------------------------------------------------------------

  /**
   * Serialize this SenderKeyRecord into a Uint8Array.
   * Mirrors: Native.SenderKeyRecord_GetSerialized
   */
  serialize(): Uint8Array {
    return wasm.senderkeyrecord_get_serialized(this.handle);
  }

  // --------------------------------------------------------------
  // Lifetime
  // --------------------------------------------------------------

  /**
   * Destroy the underlying native handle.
   * Mirrors: Native.SenderKeyRecord_Destroy
   */
  destroy(): void {
    wasm.senderkeyrecord_destroy(this.handle);
  }
}