export interface SignedPreKeyRecordWasm {
  signed_prekey_new(
    id: number,
    timestamp: number,
    pubKeyPtr: number,
    privKeyPtr: number,
    signature: Uint8Array
  ): number;

  signed_prekey_deserialize(data: Uint8Array): number;

  signed_prekey_get_id(ptr: number): number;
  signed_prekey_get_timestamp(ptr: number): number;

  signed_prekey_get_public_key(ptr: number): number;
  signed_prekey_get_private_key(ptr: number): number;

  signed_prekey_get_signature(ptr: number): Uint8Array;

  signed_prekey_serialize(ptr: number): Uint8Array;

  signed_prekey_destroy(ptr: number): void;
}