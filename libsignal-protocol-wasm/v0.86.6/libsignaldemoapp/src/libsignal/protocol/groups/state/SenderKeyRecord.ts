import { senderKeyRecord as  senderKeyRecordWasm } from "libsignal_wasm_pqxdh";

/**
 * Equivalent of:
 * org.signal.libsignal.protocol.groups.state.SenderKeyRecord
 *
 * A durable representation of SenderKeyStates for a
 * (senderName, deviceId, distributionId) tuple.
 *
 * Native-backed, handle-owning object.
 */
export class SenderKeyRecord {
  public readonly handle: number;
  private destroyed = false;

  /**
   * Called from native (WASM) with an existing handle.
   * Mirrors @CalledFromNative constructor.
   */
  constructor(handle: number);

  /**
   * Deserialize from serialized bytes.
   * Throws InvalidMessageException-equivalent on failure.
   */
  constructor(serialized: Uint8Array);

  constructor(arg: number | Uint8Array) {
    if (typeof arg === "number") {
      this.handle = arg;
    } else {
      // Deserialize path
      this.handle = senderKeyRecordWasm.senderkeyrecord_deserialize(arg);
    }
  }

  /**
   * Serialize this SenderKeyRecord.
   */
  serialize(): Uint8Array {
    return senderKeyRecordWasm.senderkeyrecord_serialize(this.handle);
  }

  /**
   * Explicitly release native resources.
   * MUST be called when record is no longer used.
   */
  destroy(): void {
    if (!this.destroyed) {
      senderKeyRecordWasm.senderkeyrecord_destroy(this.handle);
      this.destroyed = true;
    }
  }
}
