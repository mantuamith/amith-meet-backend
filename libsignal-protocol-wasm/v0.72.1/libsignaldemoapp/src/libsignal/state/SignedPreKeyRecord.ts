import { InvalidKeyException } from "../exceptions/InvalidKeyException";
import { InvalidMessageException } from "../exceptions/InvalidMessageException";
import { ECKeyPair } from "../protocol/ecc/ECKeyPair";
import { ECPrivateKey } from "../protocol/ecc/ECPrivateKey";
import { ECPublicKey } from "../protocol/ecc/ECPublicKey";
import { signedPreKeyRecord as  signedPreKeyRecordWasm } from "libsignal_wasm_pqxdh";

export class SignedPreKeyRecord {

  public readonly handle: number;

  // --------------------------------------------------------------------------
  // Constructors
  // --------------------------------------------------------------------------

  /**
   * new SignedPreKeyRecord(id, timestamp, keyPair, signature)
   */
  constructor(id: number, timestamp: number, keyPair: ECKeyPair, signature: Uint8Array);

  /**
   * new SignedPreKeyRecord(serializedBytes)
   */
  constructor(serialized: Uint8Array);

  constructor(arg1: any, arg2?: any, arg3?: any, arg4?: any) {

    // Case 1: new SignedPreKeyRecord(serialized)
    if (arg1 instanceof Uint8Array) {
      try {
        this.handle = signedPreKeyRecordWasm.signed_prekey_deserialize(arg1);
      } catch (_) {
        throw new InvalidMessageException("Failed to deserialize SignedPreKeyRecord");
      }
      return;
    }

    // Case 2: new SignedPreKeyRecord(id, timestamp, keyPair, signature)
    const id = arg1 as number;
    const timestamp = arg2 as number;
    const keyPair = arg3 as ECKeyPair;
    const signature = arg4 as Uint8Array;

    const pubPtr = keyPair.publicKey.handle;
    const privPtr = keyPair.privateKey.handle;

    this.handle = signedPreKeyRecordWasm.signed_prekey_new(
      id,
      BigInt(timestamp),
      pubPtr,
      privPtr,
      signature
    );
  }

  // --------------------------------------------------------------------------
  // API Methods
  // --------------------------------------------------------------------------
  getId(): number {
    return signedPreKeyRecordWasm.signed_prekey_get_id(this.handle);
  }

  getTimestamp(): BigInt {
    return signedPreKeyRecordWasm.signed_prekey_get_timestamp(this.handle);
  }

  /**
   * Returns ECKeyPair(publicKey, privateKey)
   */
  getKeyPair(): ECKeyPair {
    try {
      const pubPtr = signedPreKeyRecordWasm.signed_prekey_get_public_key(this.handle);
      const privPtr = signedPreKeyRecordWasm.signed_prekey_get_private_key(this.handle);

      const publicKey = new ECPublicKey(pubPtr);
      const privateKey = new ECPrivateKey(privPtr);

      return new ECKeyPair(publicKey, privateKey);
    } catch (e) {
      throw new InvalidKeyException("Invalid key pair inside SignedPreKeyRecord");
    }
  }

  getSignature(): Uint8Array {
    return signedPreKeyRecordWasm.signed_prekey_get_signature(this.handle);
  }

  serialize(): Uint8Array {
    return signedPreKeyRecordWasm.signed_prekey_serialize(this.handle);
  }

  /**
   * Explicit cleanup (mirrors NativeHandleGuard.release)
   */
  destroy(): void {
    signedPreKeyRecordWasm.signed_prekey_destroy(this.handle);
  }
}