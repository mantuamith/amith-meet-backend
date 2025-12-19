import { InvalidKeyException } from "../../exceptions/InvalidKeyException";
import { KEMKeyType } from "./KEMKeyType";
import { KEMPublicKey } from "./KEMPublicKey";
import { KEMSecretKey } from "./KEMSecretKey";

// Import WASM interface
import { kemKeyPair as kemKeyPairWasm } from "../../../../../libsignal-protocol-wasm/wasm-wrapper/pkg/libsignal_wasm_pqxdh";


/**
 * A TypeScript equivalent of Signal's KEMKeyPair.
 * Wraps WASM handles for public/secret Kyber keys.
 */
export class KEMKeyPair {
  readonly handle: number;
  private pubPtr?: number;
  private secPtr?: number;

  // -------------------------------------------------------------
  // Constructor overload signatures (TYPE ONLY)
  // -------------------------------------------------------------
  constructor(handle: number);
  constructor(handle: number, publicPtr: number, secretPtr: number);

  // -------------------------------------------------------------
  // Constructor implementation (SINGLE)
  // -------------------------------------------------------------
  constructor(handle: number, publicPtr?: number, secretPtr?: number) {
    if (!handle || handle === 0) {
      throw new InvalidKeyException("Invalid KEMKeyPair handle");
    }

    this.handle = handle;

    if (publicPtr !== undefined) {
      if (!publicPtr || publicPtr === 0) {
        throw new InvalidKeyException("Invalid KEM public key handle");
      }
      this.pubPtr = publicPtr;
    }

    if (secretPtr !== undefined) {
      if (!secretPtr || secretPtr === 0) {
        throw new InvalidKeyException("Invalid KEM secret key handle");
      }
      this.secPtr = secretPtr;
    }
  }

  // -------------------------------------------------------------
  // Static: Generate new keypair
  // -------------------------------------------------------------
  static generate(type: KEMKeyType): KEMKeyPair {
    switch (type) {
      case KEMKeyType.KYBER_1024: {
        const handle = kemKeyPairWasm.kyberkeypair_generate();
        if (!handle || handle === 0) {
          throw new Error("Failed to generate Kyber keypair");
        }
        return new KEMKeyPair(handle);
      }

      default:
        throw new Error(`Unsupported KEMKeyType: ${type}`);
    }
  }

  // -------------------------------------------------------------
  // Static: Construct from existing keys
  // -------------------------------------------------------------
  static fromKeys(
    publicKey: KEMPublicKey,
    secretKey: KEMSecretKey
  ): KEMKeyPair {
    if (!publicKey?.handle || publicKey.handle === 0) {
      throw new InvalidKeyException("Invalid KEM public key");
    }

    if (!secretKey?.handle || secretKey.handle === 0) {
      throw new InvalidKeyException("Invalid KEM secret key");
    }

    const handle = kemKeyPairWasm.kyberkeypair_from_keys(
      publicKey.handle,
      secretKey.handle
    );

    if (!handle || handle === 0) {
      throw new Error("Failed to create Kyber keypair from existing keys");
    }

    return new KEMKeyPair(handle, publicKey.handle, secretKey.handle);
  }

  // -------------------------------------------------------------
  // Accessors
  // -------------------------------------------------------------
  get publicKey(): KEMPublicKey {
    if (this.pubPtr) {
      return new KEMPublicKey(this.pubPtr);
    }

    const pubPtr = kemKeyPairWasm.kyberkeypair_get_public_key(this.handle);
    if (!pubPtr || pubPtr === 0) {
      throw new InvalidKeyException("Failed to load public key from KEMKeyPair");
    }

    this.pubPtr = pubPtr;
    return new KEMPublicKey(pubPtr);
  }

  get secretKey(): KEMSecretKey {
    if (this.secPtr) {
      return new KEMSecretKey(this.secPtr);
    }

    const secPtr = kemKeyPairWasm.kyberkeypair_get_secret_key(this.handle);
    if (!secPtr || secPtr === 0) {
      throw new InvalidKeyException("Failed to load secret key from KEMKeyPair");
    }

    this.secPtr = secPtr;
    return new KEMSecretKey(secPtr);
  }

  // -------------------------------------------------------------
  // Cleanup
  // -------------------------------------------------------------
  destroy(): void {
    kemKeyPairWasm.kyberkeypair_destroy(this.handle);
  }
}
