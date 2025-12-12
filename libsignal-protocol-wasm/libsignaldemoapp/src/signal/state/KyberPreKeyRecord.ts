import { InvalidKeyException } from "../exceptions/InvalidKeyException";
import { InvalidMessageException } from "../exceptions/InvalidMessageException";
import { KEMKeyPair } from "../protocol/kem/KEMKeyPair";
import type { KyberPreKeyRecordWasm } from "../wasm/KyberPreKeyRecordWasm";

declare const wasm: KyberPreKeyRecordWasm;

export class KyberPreKeyRecord {
  public readonly handle: number;

  // ----------------------------------------------------------------------
  // Constructors
  // ----------------------------------------------------------------------

  /**
   * new KyberPreKeyRecord(id, timestamp, keyPair, signature)
   */
  constructor(id: number, timestamp: number, keyPair: KEMKeyPair, signature: Uint8Array);

  /**
   * new KyberPreKeyRecord(serializedBytes)
   */
  constructor(serialized: Uint8Array);

  constructor(arg1: any, arg2?: any, arg3?: any, arg4?: any) {
    // Case 1: `new KyberPreKeyRecord(serialized)`
    if (arg1 instanceof Uint8Array) {
      try {
        this.handle = wasm.kyber_prekey_deserialize(arg1);
      } catch (_) {
        throw new InvalidMessageException("Failed to deserialize KyberPreKeyRecord");
      }
      return;
    }

    // Case 2: new KyberPreKeyRecord(id, timestamp, keyPair, signature)
    const id = arg1 as number;
    const timestamp = arg2 as number;
    const keyPair = arg3 as KEMKeyPair;
    const signature = arg4 as Uint8Array;

    const keyPairHandle = keyPair.handle;

    this.handle = wasm.kyber_prekey_new(
      id,
      timestamp,
      keyPairHandle,
      signature
    );
  }

  // ----------------------------------------------------------------------
  // API Methods
  // ----------------------------------------------------------------------

  getId(): number {
    return wasm.kyber_prekey_get_id(this.handle);
  }

  getTimestamp(): number {
    return wasm.kyber_prekey_get_timestamp(this.handle);
  }

  /**
   * Returns a new KEMKeyPair constructed from the underlying native pointer.
   */
  getKeyPair(): KEMKeyPair {
    try {
      const kpHandle = wasm.kyber_prekey_get_keypair(this.handle);
      return new KEMKeyPair(kpHandle);
    } catch (e) {
      throw new InvalidKeyException(
        `Error retrieving Kyber key pair: ${(e as Error).message}`
      );
    }
  }

  getSignature(): Uint8Array {
    return wasm.kyber_prekey_get_signature(this.handle);
  }

  serialize(): Uint8Array {
    return wasm.kyber_prekey_serialize(this.handle);
  }

  /**
   * Equivalent to Java's release(nativeHandle)
   */
  destroy(): void {
    wasm.kyber_prekey_destroy(this.handle);
  }
}