export interface KEMPublicKeyWasm {
  kyberpublickey_deserialize(bytes: Uint8Array, offset: number, length: number): number;
  kyberpublickey_destroy(ptr: number): void;
  kyberpublickey_serialize(ptr: number): Uint8Array;
  kyberpublickey_equals(ptrA: number, ptrB: number): boolean;
}