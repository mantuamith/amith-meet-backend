export interface ECPublicKeyWasm {
  ec_public_key_deserialize(bytes: Uint8Array, offset: number, length: number): number;
  ec_public_key_destroy(ptr: number): void;

  ec_public_key_verify(ptr: number, message: Uint8Array, signature: Uint8Array): boolean;

  ec_public_key_hpke_seal(
    ptr: number,
    message: Uint8Array,
    info: Uint8Array,
    aad: Uint8Array
  ): Uint8Array;

  ec_public_key_serialize(ptr: number): Uint8Array;
  ec_public_key_get_public_key_bytes(ptr: number): Uint8Array;

  ec_public_key_equals(ptrA: number, ptrB: number): boolean;
  ec_public_key_compare(ptrA: number, ptrB: number): number;
}