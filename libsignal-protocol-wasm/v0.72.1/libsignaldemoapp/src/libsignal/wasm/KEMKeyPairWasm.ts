export interface KEMKeyPairWasm {
  kyberkeypair_generate(): number;
  kyberkeypair_destroy(ptr: number): void;

  kyberkeypair_get_public_key(ptr: number): number;
  kyberkeypair_get_secret_key(ptr: number): number;
}