export interface IdentityKeyPairWasm {
  identitykeypair_deserialize(bytes: Uint8Array): { publicKeyPtr: number; privateKeyPtr: number };
  identitykeypair_serialize(publicPtr: number, privatePtr: number): Uint8Array;
  identitykeypair_sign_alternate_identity(
    publicPtr: number,
    privatePtr: number,
    otherPublicPtr: number
  ): Uint8Array;
}