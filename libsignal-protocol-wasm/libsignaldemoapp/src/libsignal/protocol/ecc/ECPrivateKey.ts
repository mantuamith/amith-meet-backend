import { InvalidKeyException } from "../../exceptions/InvalidKeyException";
import { InvalidMessageException } from "../../exceptions/InvalidMessageException";
import { ECPublicKey } from "./ECPublicKey";
import { ecPrivateKey as  ecPrivateKeyWasm } from "libsignal_wasm_pqxdh";

/**
 * TypeScript Equivalent of `ECPrivateKey`
 */
export class ECPrivateKey {
  /** Native pointer */
  readonly handle: number;

  // --------------------------------------------------------------------------
  // Constructors
  // --------------------------------------------------------------------------

  /**
   * Construct from serialized bytes
   */
  constructor(privateKeyBytes: Uint8Array);

  /**
   * Construct from native handle
   */
  constructor(nativeHandle: number);

  constructor(arg: any) {
    if (arg instanceof Uint8Array) {
      try {
        this.handle = ecPrivateKeyWasm.ecprivatekey_deserialize(arg);
      } catch (e) {
        throw new InvalidKeyException(`Failed to deserialize ECPrivateKey: ${(e as Error).message}`);
      }
    } else {
      // native handle constructor
      this.handle = arg;
    }
  }

  // --------------------------------------------------------------------------
  // Key generation
  // --------------------------------------------------------------------------

  static generate(): ECPrivateKey {
    const ptr = ecPrivateKeyWasm.ecprivatekey_generate();
    return new ECPrivateKey(ptr);
  }

  // --------------------------------------------------------------------------
  // Lifecycle
  // --------------------------------------------------------------------------

  destroy(): void {
    ecPrivateKeyWasm.ecprivatekey_destroy(this.handle);
  }

  // --------------------------------------------------------------------------
  // Serialization
  // --------------------------------------------------------------------------

  serialize(): Uint8Array {
    return ecPrivateKeyWasm.ecprivatekey_serialize(this.handle);
  }

  // --------------------------------------------------------------------------
  // Signature
  // --------------------------------------------------------------------------

  calculateSignature(message: Uint8Array): Uint8Array {
    return ecPrivateKeyWasm.ecprivatekey_sign(this.handle, message);
  }

  // --------------------------------------------------------------------------
  // Agreement (ECDH)
  // --------------------------------------------------------------------------

  calculateAgreement(other: ECPublicKey): Uint8Array {
    return ecPrivateKeyWasm.ecprivatekey_agree(this.handle, other.handle);
  }

  // --------------------------------------------------------------------------
  // HPKE open
  // --------------------------------------------------------------------------

  open(
    ciphertext: Uint8Array,
    info: Uint8Array,
    associatedData: Uint8Array = new Uint8Array()
  ): Uint8Array {
    try {
      return ecPrivateKeyWasm.ecprivatekey_hpke_open(this.handle, ciphertext, info, associatedData);
    } catch (e) {
      // Match Java: can throw InvalidMessageException or IllegalArgumentException
      throw new InvalidMessageException((e as Error).message);
    }
  }

  /**
   * Convenience overload: `info` as UTF-8 string
   */
  openStringInfo(
    ciphertext: Uint8Array,
    info: string,
    associatedData: Uint8Array = new Uint8Array()
  ): Uint8Array {
    return this.open(ciphertext, new TextEncoder().encode(info), associatedData);
  }

  // --------------------------------------------------------------------------
  // Public Key
  // --------------------------------------------------------------------------

  getPublicKey(): ECPublicKey {
    const ptr = ecPrivateKeyWasm.ecprivatekey_get_public_key(this.handle);
    return new ECPublicKey(ptr);
  }
}