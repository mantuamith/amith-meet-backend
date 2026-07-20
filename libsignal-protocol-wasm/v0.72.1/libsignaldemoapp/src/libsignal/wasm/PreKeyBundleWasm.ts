export interface PreKeyBundleWasm {
  prekeybundle_new(
    registrationId: number,
    deviceId: number,
    preKeyId: number,
    preKeyPtr: number,
    signedPreKeyId: number,
    signedPreKeyPtr: number,
    signedPreKeySignature: Uint8Array,
    identityPtr: number,
    kyberPreKeyId: number,
    kyberPtr: number,
    kyberPreKeySignature: Uint8Array
  ): number;

  prekeybundle_destroy(ptr: number): void;

  prekeybundle_get_device_id(ptr: number): number;
  prekeybundle_get_prekey_id(ptr: number): number;
  prekeybundle_get_prekey_public(ptr: number): number;

  prekeybundle_get_signed_prekey_id(ptr: number): number;
  prekeybundle_get_signed_prekey_public(ptr: number): number;
  prekeybundle_get_signed_prekey_signature(ptr: number): Uint8Array;

  prekeybundle_get_identity_key(ptr: number): number;

  prekeybundle_get_registration_id(ptr: number): number;

  prekeybundle_get_kyber_prekey_id(ptr: number): number;
  prekeybundle_get_kyber_prekey_public(ptr: number): number;
  prekeybundle_get_kyber_prekey_signature(ptr: number): Uint8Array;
}
