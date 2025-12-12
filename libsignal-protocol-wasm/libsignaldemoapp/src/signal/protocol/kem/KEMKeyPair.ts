import { InvalidKeyException } from "../../exceptions/InvalidKeyException";
import { KEMKeyType } from "./KEMKeyType";
import { KEMPublicKey } from "./KEMPublicKey";
import { KEMSecretKey } from "./KEMSecretKey";

// Import WASM interface
import { kemKeyPair as kemKeyPairWasm } from "libsignal_wasm_pqxdh";


/**
 * A TypeScript equivalent of Signal's KEMKeyPair.
 * Wraps WASM handles for public/secret Kyber keys.
 */
export class KEMKeyPair {
  readonly handle: number;

  // -------------------------------------------------------------
  // Constructor (internal)
  // -------------------------------------------------------------
  constructor(handle: number) {
    if (!handle || handle === 0) {
      throw new InvalidKeyException("Invalid KEMKeyPair handle");
    }
    this.handle = handle;
  }

  // -------------------------------------------------------------
  // Static: Generate new keypair
  // -------------------------------------------------------------
  static generate(type: KEMKeyType): KEMKeyPair {
    switch (type) {
      case KEMKeyType.KYBER_1024: {
        const ptr = kemKeyPairWasm.kyberkeypair_generate();
        if (!ptr || ptr === 0) {
          throw new Error("Failed to generate Kyber keypair");
        }
        return new KEMKeyPair(ptr);
      }

      default:
        throw new Error(`Unsupported KEMKeyType: ${type}`);
    }
  }

  // -------------------------------------------------------------
  // Public accessors
  // -------------------------------------------------------------

  /**
   * Return the Kyber public key associated with this keypair.
   */
  get publicKey(): KEMPublicKey {
    const pubPtr = kemKeyPairWasm.kyberkeypair_get_public_key(this.handle);

    if (!pubPtr || pubPtr === 0) {
      throw new InvalidKeyException("Failed to load public key from KEMKeyPair");
    }

    return new KEMPublicKey(pubPtr);
  }

  /**
   * Return the Kyber secret key associated with this keypair.
   */
  get secretKey(): KEMSecretKey {
    const secPtr = kemKeyPairWasm.kyberkeypair_get_secret_key(this.handle);

    if (!secPtr || secPtr === 0) {
      throw new InvalidKeyException("Failed to load secret key from KEMKeyPair");
    }

    return new KEMSecretKey(secPtr);
  }

  // -------------------------------------------------------------
  // Cleanup
  // -------------------------------------------------------------

  destroy(): void {
    kemKeyPairWasm.kyberkeypair_destroy(this.handle);
  }
}
