import { ECPublicKey } from "../protocol/ecc/ECPublicKey";
import { IdentityKey } from "../protocol/IdentityKey";
import { KEMPublicKey } from "../protocol/kem/KEMPublicKey";
import { InvalidKeyException } from "../exceptions/InvalidKeyException";

import { preKeyBundle as preKeyBundleWasm } from "libsignal_wasm_pqxdh";

/**
 * TypeScript version of Signal's PreKeyBundle
 */
export class PreKeyBundle {

  static readonly NULL_PRE_KEY_ID = -1;

  /** Native WASM handle */
  readonly handle: number;

  // --------------------------------------------------------------------
  // Constructor
  // --------------------------------------------------------------------

  constructor(
    registrationId: number,
    deviceId: number,
    preKeyId: number,
    preKeyPublic: ECPublicKey | null,
    signedPreKeyId: number,
    signedPreKeyPublic: ECPublicKey,
    signedPreKeySignature: Uint8Array,
    identityKey: IdentityKey,
    kyberPreKeyId: number,
    kyberPreKeyPublic: KEMPublicKey,
    kyberPreKeySignature: Uint8Array
  ) {
    const preKeyPtr = preKeyPublic ? preKeyPublic.handle : 0;
    const signedPreKeyPtr = signedPreKeyPublic.handle;
    const identityPtr = identityKey.getPublicKey().handle;
    const kyberPtr = kyberPreKeyPublic.handle;

    const native = preKeyBundleWasm.prekeybundle_new(
      registrationId,
      deviceId,
      preKeyId,
      preKeyPtr,
      signedPreKeyId,
      signedPreKeyPtr,
      signedPreKeySignature,
      identityPtr,
      kyberPreKeyId,
      kyberPtr,
      kyberPreKeySignature
    );

    if (!native) {
      throw new InvalidKeyException("Failed to create PreKeyBundle");
    }

    this.handle = native;
  }

  destroy(): void {
    preKeyBundleWasm.prekeybundle_destroy(this.handle);
  }

  // --------------------------------------------------------------------
  // Getters (mirror Kotlin/Java API)
  // --------------------------------------------------------------------

  get deviceId(): number {
    return preKeyBundleWasm.prekeybundle_get_device_id(this.handle);
  }

  get preKeyId(): number {
    return preKeyBundleWasm.prekeybundle_get_prekey_id(this.handle);
  }

  get preKey(): ECPublicKey | null {
    const ptr = preKeyBundleWasm.prekeybundle_get_prekey_public(this.handle);
    return ptr === 0 ? null : new ECPublicKey(ptr);
  }

  get signedPreKeyId(): number {
    return preKeyBundleWasm.prekeybundle_get_signed_prekey_id(this.handle);
  }

  get signedPreKey(): ECPublicKey {
    const ptr = preKeyBundleWasm.prekeybundle_get_signed_prekey_public(this.handle);
    return new ECPublicKey(ptr);
  }

  get signedPreKeySignature(): Uint8Array {
    return preKeyBundleWasm.prekeybundle_get_signed_prekey_signature(this.handle);
  }

  get identityKey(): IdentityKey {
    const ptr = preKeyBundleWasm.prekeybundle_get_identity_key(this.handle);
    return new IdentityKey(new ECPublicKey(ptr));
  }

  get registrationId(): number {
    return preKeyBundleWasm.prekeybundle_get_registration_id(this.handle);
  }

  get kyberPreKeyId(): number {
    return preKeyBundleWasm.prekeybundle_get_kyber_prekey_id(this.handle);
  }

  get kyberPreKey(): KEMPublicKey {
    const ptr = preKeyBundleWasm.prekeybundle_get_kyber_prekey_public(this.handle);
    return new KEMPublicKey(ptr);
  }

  get kyberPreKeySignature(): Uint8Array {
    return preKeyBundleWasm.prekeybundle_get_kyber_prekey_signature(this.handle);
  }
}
