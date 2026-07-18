// Expected WASM API for IdentityKey operations
export interface IdentityKeyWasm {
  identity_verify_alternate_identity(
    pubKeyPtr: number,
    otherPtr: number,
    signature: Uint8Array
  ): boolean;
}