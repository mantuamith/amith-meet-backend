import { ECPrivateKey } from "./ECPrivateKey";
import type { ECPublicKey } from "./ECPublicKey";

/**
 * TypeScript equivalent of Signal's ECKeyPair.
 *
 * A simple container holding an EC public/private key pair.
 */
export class ECKeyPair {
  public readonly publicKey: ECPublicKey;
  public readonly privateKey: ECPrivateKey;

  constructor(publicKey: ECPublicKey, privateKey: ECPrivateKey) {
    this.publicKey = publicKey;
    this.privateKey = privateKey;
  }

  /**
   * Equivalent of:
   *   ECPrivateKey privateKey = ECPrivateKey.generate();
   *   return new ECKeyPair(privateKey.getPublicKey(), privateKey);
   */
  static generate(): ECKeyPair {
    const privateKey = ECPrivateKey.generate();
    const publicKey = privateKey.getPublicKey();
    return new ECKeyPair(publicKey, privateKey);
  }
}