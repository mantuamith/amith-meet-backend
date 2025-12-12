import { InvalidKeyException } from "../exceptions/InvalidKeyException";
import type { IdentityKeyWasm } from "../wasm/IdentityKeyWasm";
import { ECPublicKey } from "./ecc/ECPublicKey";

declare const wasm: IdentityKeyWasm;

export class IdentityKey {
  public readonly publicKey: ECPublicKey;

  // ---- Constructors ----

  constructor(publicKey: ECPublicKey);
  constructor(bytes: Uint8Array, offset: number);
  constructor(bytes: Uint8Array);
  constructor(nativeHandle: number);
  constructor(arg1: any, arg2?: any) {
    // 1. new IdentityKey(publicKey: ECPublicKey)
    if (arg1 instanceof ECPublicKey) {
      this.publicKey = arg1;
      return;
    }

    // 2. new IdentityKey(bytes: Uint8Array, offset: number)
    if (arg1 instanceof Uint8Array && typeof arg2 === "number") {
      this.publicKey = new ECPublicKey(arg1, arg2);
      return;
    }

    // 3. new IdentityKey(bytes: Uint8Array)
    if (arg1 instanceof Uint8Array) {
      this.publicKey = new ECPublicKey(arg1, 0);
      return;
    }

    // 4. new IdentityKey(nativeHandle: number)
    if (typeof arg1 === "number") {
      this.publicKey = new ECPublicKey(arg1);
      return;
    }

    throw new InvalidKeyException("Invalid IdentityKey constructor arguments");
  }

  // ---- API ----

  getPublicKey(): ECPublicKey {
    return this.publicKey;
  }

  serialize(): Uint8Array {
    return this.publicKey.serialize();
  }

  /** Equivalent of Java's Hex encoding. */
  getFingerprint(): string {
    const bytes = this.publicKey.serialize();
    return [...bytes].map(b => b.toString(16).padStart(2, "0")).join("");
  }

  /**
   * Verify this IdentityKey against an alternate one.
   * Equivalent of Native.IdentityKey_VerifyAlternateIdentity(...)
   */
  verifyAlternateIdentity(other: IdentityKey, signature: Uint8Array): boolean {
    return wasm.identity_verify_alternate_identity(
      this.publicKey.handle,
      other.publicKey.handle,
      signature
    );
  }

  // ---- Equality / Hashing ----

  equals(other: any): boolean {
    if (!other || !(other instanceof IdentityKey)) return false;
    return this.publicKey.equals(other.publicKey);
  }

  hashCode(): number {
    return this.publicKey.hashCode();
  }
}