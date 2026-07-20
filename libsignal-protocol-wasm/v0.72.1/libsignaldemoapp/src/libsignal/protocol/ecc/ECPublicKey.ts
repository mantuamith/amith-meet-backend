import { InvalidKeyException } from "../../exceptions/InvalidKeyException";
import { ecPublicKey as  ecPublicKeyWasm } from "libsignal_wasm_pqxdh";

export class ECPublicKey {
  static readonly KEY_SIZE = 33;

  /** Native handle managed by WASM */
  public readonly handle: number;

  // --------------------------------------------------------------------
  // Constructors
  // --------------------------------------------------------------------

  constructor(serialized: Uint8Array, offset: number);
  constructor(serialized: Uint8Array);
  constructor(serialized: Uint8Array, offset: number, length: number);
  constructor(nativeHandle: number);
  constructor(arg1: any, arg2?: any, arg3?: any) {
    // Case 1: new ECPublicKey(nativeHandle: number)
    if (typeof arg1 === "number") {
      //if (arg1 === 0) throw new Error("Null native handle for ECPublicKey");
      this.handle = arg1;
      return;
    }

    // Case 2: new ECPublicKey(bytes)
    if (arg1 instanceof Uint8Array && arg2 === undefined) {
      const bytes = arg1;
      this.handle = ecPublicKeyWasm.ec_public_key_deserialize(bytes, 0, bytes.length);
      return;
    }

    // Case 3: new ECPublicKey(bytes, offset)
    if (arg1 instanceof Uint8Array && typeof arg2 === "number" && arg3 === undefined) {
      const bytes = arg1;
      const offset = arg2;
      const length = bytes.length - offset;
      this.handle = ecPublicKeyWasm.ec_public_key_deserialize(bytes, offset, length);
      return;
    }

    // Case 4: new ECPublicKey(bytes, offset, length)
    if (arg1 instanceof Uint8Array && typeof arg2 === "number" && typeof arg3 === "number") {
      const bytes = arg1;
      const offset = arg2;
      const length = arg3;
      this.handle = ecPublicKeyWasm.ec_public_key_deserialize(bytes, offset, length);
      return;
    }

    throw new InvalidKeyException("Invalid ECPublicKey constructor arguments");
  }

  // --------------------------------------------------------------------
  // Companion: fromPublicKeyBytes
  // --------------------------------------------------------------------

  static fromPublicKeyBytes(key: Uint8Array): ECPublicKey {
    if (key.length !== ECPublicKey.KEY_SIZE - 1) {
      throw new InvalidKeyException(
        `invalid number of public key bytes (expected ${ECPublicKey.KEY_SIZE - 1}, was ${key.length})`
      );
    }

    const withType = new Uint8Array(ECPublicKey.KEY_SIZE);
    withType[0] = 0x05; // The type byte
    withType.set(key, 1);

    return new ECPublicKey(withType);
  }

  // --------------------------------------------------------------------
  // API Methods
  // --------------------------------------------------------------------

  verifySignature(message: Uint8Array, signature: Uint8Array): boolean {
    return ecPublicKeyWasm.ec_public_key_verify(this.handle, message, signature);
  }

  /**
   * HPKE seal() – encrypts a message so only the private key holder can decrypt it.
   */
  seal(message: Uint8Array, info: Uint8Array, associatedData: Uint8Array = new Uint8Array()): Uint8Array {
    return ecPublicKeyWasm.ec_public_key_hpke_seal(this.handle, message, info, associatedData);
  }

  /** Overload: seal(message, infoString) */
  sealWithStringInfo(message: Uint8Array, info: string, associatedData: Uint8Array = new Uint8Array()): Uint8Array {
    return this.seal(message, new TextEncoder().encode(info), associatedData);
  }

  serialize(): Uint8Array {
    return ecPublicKeyWasm.ec_public_key_serialize(this.handle);
  }

  get publicKeyBytes(): Uint8Array {
    return ecPublicKeyWasm.ec_public_key_get_public_key_bytes(this.handle);
  }

  get type(): number {
    return this.serialize()[0];
  }

  // --------------------------------------------------------------------
  // Equality / Hashing / Comparison
  // --------------------------------------------------------------------

  equals(other: any): boolean {
    if (!(other instanceof ECPublicKey)) return false;
    return ecPublicKeyWasm.ec_public_key_equals(this.handle, other.handle);
  }

  hashCode(): number {
    const bytes = this.serialize();
    let hash = 0;
    for (const b of bytes) hash = ((hash << 5) - hash) + b;
    return hash | 0;
  }

  /* TODO:
  compareTo(other: ECPublicKey): number {
    return ecPublicKeyWasm.ec_public_key_compare(this.handle, other.handle);
  }*/

  // --------------------------------------------------------------------
  // Manual cleanup (Java uses NativeHandleGuard)
  // --------------------------------------------------------------------

  destroy(): void {
    ecPublicKeyWasm.ec_public_key_destroy(this.handle);
  }
}