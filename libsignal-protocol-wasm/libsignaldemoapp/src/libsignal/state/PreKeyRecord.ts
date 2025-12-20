// ------------------------------------------------------
// PreKeyRecord TypeScript Equivalent

import { InvalidKeyException } from "../exceptions/InvalidKeyException";
import { InvalidMessageException } from "../exceptions/InvalidMessageException";
import { ECKeyPair } from "../protocol/ecc/ECKeyPair";
import { ECPrivateKey } from "../protocol/ecc/ECPrivateKey";
import { ECPublicKey } from "../protocol/ecc/ECPublicKey";
import { preKeyRecord as  preKeyRecordWasm } from "libsignal_wasm_pqxdh";

/**
 * TypeScript equivalent of Signal's PreKeyRecord.
 */
export class PreKeyRecord {
  public readonly handle: number;

  // --------------------------------------------------------------
  // Constructors
  // --------------------------------------------------------------

  /**
   * Construct from (id, ECKeyPair)
   */
  constructor(id: number, keyPair: ECKeyPair);

  /**
   * Construct from serialized bytes
   */
  constructor(serialized: Uint8Array);

  constructor(arg1: any, arg2?: any) {

    // Case 1: new PreKeyRecord(serializedBytes)
    if (arg1 instanceof Uint8Array) {
      try {
        this.handle = preKeyRecordWasm.prekeyrecord_deserialize(arg1);
      } catch (_) {
        throw new InvalidMessageException("Failed to deserialize PreKeyRecord");
      }
      return;
    }

    // Case 2: new PreKeyRecord(id, keyPair)
    const id = arg1 as number;
    const keyPair = arg2 as ECKeyPair;

    const pubPtr = keyPair.publicKey.handle;
    const privPtr = keyPair.privateKey.handle;

    this.handle = preKeyRecordWasm.prekeyrecord_new(id, pubPtr, privPtr);
  }

  // --------------------------------------------------------------
  // API
  // --------------------------------------------------------------

  getId(): number {
    return preKeyRecordWasm.prekeyrecord_get_id(this.handle);
  }

  /**
   * Return ECKeyPair(publicKey, privateKey)
   */
  getKeyPair(): ECKeyPair {
    try {
      const publicPtr = preKeyRecordWasm.prekeyrecord_get_public_key(this.handle);
      const privatePtr = preKeyRecordWasm.prekeyrecord_get_private_key(this.handle);

      const pub = new ECPublicKey(publicPtr);
      const priv = new ECPrivateKey(privatePtr);
      return new ECKeyPair(pub, priv);

    } catch (e) {
      throw new InvalidKeyException(
        `Failed to extract key pair from PreKeyRecord: ${(e as Error).message}`
      );
    }
  }

  serialize(): Uint8Array {
    return preKeyRecordWasm.prekeyrecord_get_serialized(this.handle);
  }

  destroy(): void {
    preKeyRecordWasm.prekeyrecord_destroy(this.handle);
  }
}