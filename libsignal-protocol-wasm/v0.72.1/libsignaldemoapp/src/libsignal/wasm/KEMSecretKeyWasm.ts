export interface KEMSecretKeyWasm {
  kybersecretkey_deserialize(bytes: Uint8Array): number;
  kybersecretkey_destroy(ptr: number): void;
  kybersecretkey_serialize(ptr: number): Uint8Array;
}
