/**
 * WASM bindings for EC private key operations.
 * Must correspond to:
 *   - ECPrivateKey_Generate
 *   - ECPrivateKey_Deserialize
 *   - ECPrivateKey_Destroy
 *   - ECPrivateKey_Serialize
 *   - ECPrivateKey_Sign
 *   - ECPrivateKey_Agree
 *   - ECPrivateKey_GetPublicKey
 *   - ECPrivateKey_HpkeOpen
 */
export interface ECPrivateKeyWasm {
  ecprivatekey_generate(): number;
  ecprivatekey_deserialize(bytes: Uint8Array): number;
  ecprivatekey_destroy(ptr: number): void;

  ecprivatekey_serialize(ptr: number): Uint8Array;
  ecprivatekey_sign(ptr: number, message: Uint8Array): Uint8Array;

  ecprivatekey_agree(ptrPriv: number, ptrPub: number): Uint8Array;

  ecprivatekey_get_public_key(ptr: number): number;

  ecprivatekey_hpke_open(
    ptr: number,
    ciphertext: Uint8Array,
    info: Uint8Array,
    aad: Uint8Array
  ): Uint8Array;
}
