import { InvalidKeyException } from "../exceptions/InvalidKeyException";
import { ECPrivateKey } from "../protocol/ecc/ECPrivateKey";
import { ECPublicKey } from "../protocol/ecc/ECPublicKey";
import { IdentityKey } from "../protocol/IdentityKey";
import { identityKeyPair as identityKeyPairWasm } from "libsignal_wasm_pqxdh";
import type { IdentityKeyPairWasm } from "../wasm/IdentityKeyPairWasm";


export class IdentityKeyPair {

  public readonly publicKey: IdentityKey;
  public readonly privateKey: ECPrivateKey;

  // -------------------------------------------------------------
  // Constructors
  // -------------------------------------------------------------

  /**
   * Construct from existing keys
   */
  constructor(publicKey: IdentityKey, privateKey: ECPrivateKey);

  /**
   * Construct from serialized bytes
   */
  constructor(serialized: Uint8Array);

  constructor(arg1: any, arg2?: any) {

    // Case 1: new IdentityKeyPair(serializedBytes)
    if (arg1 instanceof Uint8Array) {
      try {
        // Cast to interface
        const wasmInterface = identityKeyPairWasm as unknown as IdentityKeyPairWasm;
        const pair = wasmInterface.identitykeypair_deserialize(arg1);

        const pub = new ECPublicKey(pair.publicKeyPtr);
        const priv = new ECPrivateKey(pair.privateKeyPtr);

        this.publicKey = new IdentityKey(pub);
        this.privateKey = priv;
      } catch (e) {
        throw new InvalidKeyException(`Failed to deserialize IdentityKeyPair: ${(e as Error).message}`);
      }
      return;
    }

    // Case 2: new IdentityKeyPair(publicKey, privateKey)
    const publicKey = arg1 as IdentityKey;
    const privateKey = arg2 as ECPrivateKey;

    this.publicKey = publicKey;
    this.privateKey = privateKey;
  }

  // -------------------------------------------------------------
  // Static factory
  // -------------------------------------------------------------

  /**
   * Generate a new IdentityKeyPair.
   * Equivalent to:
   *   ECPrivateKey privateKey = ECPrivateKey.generate();
   *   ECPublicKey publicKey = privateKey.publicKey();
   */
  static generate(): IdentityKeyPair {
    const privateKey = ECPrivateKey.generate();
    const publicKey = new IdentityKey(privateKey.getPublicKey());
    return new IdentityKeyPair(publicKey, privateKey);
  }

  // -------------------------------------------------------------
  // Accessors
  // -------------------------------------------------------------

  getPublicKey(): IdentityKey {
    return this.publicKey;
  }

  getPrivateKey(): ECPrivateKey {
    return this.privateKey;
  }

  // -------------------------------------------------------------
  // Serialization
  // -------------------------------------------------------------

  serialize(): Uint8Array {
    const pubPtr = this.publicKey.getPublicKey().handle;
    const privPtr = this.privateKey.handle;

    return identityKeyPairWasm.identitykeypair_serialize(pubPtr, privPtr);
  }

  // -------------------------------------------------------------
  // Alternate-identity signing
  // -------------------------------------------------------------

  /**
   * Sign another IdentityKey as part of identity-change verification.
   */
  signAlternateIdentity(other: IdentityKey): Uint8Array {
    const pubPtr = this.publicKey.getPublicKey().handle;
    const privPtr = this.privateKey.handle;
    const otherPtr = other.getPublicKey().handle;

    return identityKeyPairWasm.identitykeypair_sign_alternate_identity(pubPtr, privPtr, otherPtr);
  }
}